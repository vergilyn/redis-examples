/**
 * Copyright 2016 Nikita Koksharov
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *    http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.redisson.pubsub;

import java.util.concurrent.ConcurrentMap;
import java.util.concurrent.atomic.AtomicReference;

import io.netty.util.internal.PlatformDependent;
import org.redisson.PubSubEntry;
import org.redisson.api.RFuture;
import org.redisson.client.BaseRedisPubSubListener;
import org.redisson.client.RedisPubSubListener;
import org.redisson.client.codec.LongCodec;
import org.redisson.client.protocol.pubsub.PubSubType;
import org.redisson.connection.ConnectionManager;
import org.redisson.misc.RPromise;
import org.redisson.misc.RedissonPromise;
import org.redisson.misc.TransferListener;

/**
 * 
 * @author Nikita Koksharov
 *
 */
abstract class PublishSubscribe<E extends PubSubEntry<E>> {

    private final ConcurrentMap<String, E> entries = PlatformDependent.newConcurrentHashMap();

    public void unsubscribe(final E entry, final String entryName, final String channelName, final ConnectionManager connectionManager) {
        final AsyncSemaphore semaphore = connectionManager.getSemaphore(channelName);
        semaphore.acquire(new Runnable() {
            @Override
            public void run() {
                if (entry.release() == 0) {
                    // just an assertion
                    boolean removed = entries.remove(entryName) == entry;
                    if (!removed) {
                        throw new IllegalStateException();
                    }
                    connectionManager.unsubscribe(channelName, semaphore);
                } else {
                    semaphore.release();
                }
            }
        });

    }

    public E getEntry(String entryName) {
        return entries.get(entryName);
    }

    /** vergilyn mark: <br/>
     * 订阅, RedissonLock 与 RedissonFairLock 参数不一样
     * @param entryName ex, "{Redisson.UUID}:{lock_name}:{threadId}"
     * @param channelName ex, "redisson_lock__channel:{lock_name}:{Redisson.UUID}:{threadId}"
     * @return
     */
    public RFuture<E> subscribe(final String entryName, final String channelName, final ConnectionManager connectionManager) {
        // jdk 原子操作类
        final AtomicReference<Runnable> listenerHolder = new AtomicReference<Runnable>();

        // 等价于: new AsyncSemaphore(1); 不理解为什么要这么写, 详见: getSemaphore()
        final AsyncSemaphore semaphore = connectionManager.getSemaphore(channelName);

        final RPromise<E> newPromise = new RedissonPromise<E>() {
            @Override
            public boolean cancel(boolean mayInterruptIfRunning) {
                return semaphore.remove(listenerHolder.get());
            }
        };
        //TODO vergilyn: 等理解semaphore、CountDownLatch再回来理解
        Runnable listener = new Runnable() {

            @Override
            public void run() {
                E entry = entries.get(entryName);

                // 存在同entryName未执行的listener, 则直接尝试执行
                if (entry != null) {
                    entry.aquire(); // 增加一个凭证: PubSubEntry -> counter ++;

                    // remove 首个listener, 并调用semaphore.acquire() -> 是否存在有效的凭证去执行 listener.run();
                    //  如果不存在有效的凭证, 则remove的listener重新加入到 listeners中;
                    semaphore.release();

                    // 疑问: 为什么是重复addListener(new TransferListener<E>(newPromise))?
                    // 猜测: 因为已经调用entry.aquire(), 导致 counter + 1; 所以, 会相应的执行这么多次的 TransferListener;
                    entry.getPromise().addListener(new TransferListener<E>(newPromise));
                    return;
                }
                
                E value = createEntry(newPromise); // value 等价于 entry
                value.aquire();

                // 原子操作判断, entries不存在key = entryName时, 将value加入entries
                //  否则, 返回entries.get(entryName); 并且, 不会用value去更新entries
                E oldValue = entries.putIfAbsent(entryName, value);
                if (oldValue != null) { // 与entry逻辑相同
                    oldValue.aquire();
                    semaphore.release();
                    oldValue.getPromise().addListener(new TransferListener<E>(newPromise));
                    return;
                }
                
                RedisPubSubListener<Object> listener = createListener(channelName, value);
                connectionManager.subscribe(LongCodec.INSTANCE, channelName, semaphore, listener);
            }
        };
        // 尝试执行listener, 当不存在有效凭证时, 加入到listeners(LinkedHashSet)
        semaphore.acquire(listener);
        listenerHolder.set(listener);
        
        return newPromise;
    }

    protected abstract E createEntry(RPromise<E> newPromise);

    protected abstract void onMessage(E value, Long message);

    /*
     * @param channelName ex, "redisson_lock__channel:{lock_name}:{Redisson.UUID}:{threadId}"
     * @param value ex, RedissonCountDownLatchEntry、RedissonLockEntry
     */
    private RedisPubSubListener<Object> createListener(final String channelName, final E value) {
        RedisPubSubListener<Object> listener = new BaseRedisPubSubListener() {

            @Override
            public void onMessage(String channel, Object message) {
                if (!channelName.equals(channel)) {
                    return;
                }

                PublishSubscribe.this.onMessage(value, (Long)message);
            }

            @Override
            public boolean onStatus(PubSubType type, String channel) {
                if (!channelName.equals(channel)) {
                    return false;
                }

                if (type == PubSubType.SUBSCRIBE) {
                    value.getPromise().trySuccess(value);
                    return true;
                }
                return false;
            }

        };
        return listener;
    }

}
