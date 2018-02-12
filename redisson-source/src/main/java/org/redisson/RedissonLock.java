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
package org.redisson;

import java.util.Arrays;
import java.util.Collections;
import java.util.UUID;
import java.util.concurrent.ConcurrentMap;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.atomic.AtomicReference;
import java.util.concurrent.locks.Condition;

import io.netty.util.Timeout;
import io.netty.util.TimerTask;
import io.netty.util.concurrent.Future;
import io.netty.util.concurrent.FutureListener;
import io.netty.util.internal.PlatformDependent;
import org.redisson.api.RFuture;
import org.redisson.api.RLock;
import org.redisson.client.codec.LongCodec;
import org.redisson.client.protocol.RedisCommands;
import org.redisson.client.protocol.RedisStrictCommand;
import org.redisson.command.CommandAsyncExecutor;
import org.redisson.misc.RPromise;
import org.redisson.pubsub.LockPubSub;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Distributed implementation of {@link java.util.concurrent.locks.Lock}
 * Implements reentrant lock.<br>
 * Lock will be removed automatically if template disconnects.
 * <p>
 * Implements a <b>non-fair</b> locking so doesn't guarantees an acquire order.
 *
 * @author Nikita Koksharov
 *
 */
public class RedissonLock extends RedissonExpirable implements RLock {

    private static final Logger log = LoggerFactory.getLogger(RedissonLock.class);
    /** vergilyn mark: <br/>
     * 标记已存在的 expiration-renewal TimerTask, 防止重复的TimerTask
     */
    private static final ConcurrentMap<String, Timeout> expirationRenewalMap = PlatformDependent.newConcurrentHashMap();
    protected long internalLockLeaseTime;

    final UUID id;

    protected static final LockPubSub PUBSUB = new LockPubSub();

    final CommandAsyncExecutor commandExecutor;

    public RedissonLock(CommandAsyncExecutor commandExecutor, String name, UUID id) {
        super(commandExecutor, name);
        this.commandExecutor = commandExecutor;
        this.id = id;
        this.internalLockLeaseTime = commandExecutor.getConnectionManager().getCfg().getLockWatchdogTimeout();
    }

    /** vergilyn mark: <br/>
     * @return "{RedissonLock.UUID}:{lock_name}"
     */
    protected String getEntryName() {
        return id + ":" + getName();
    }

    /** vergilyn mark: <br/>
     * @return "redisson_lock__channel:{lock_name}"
     */
    String getChannelName() {
        return prefixName("redisson_lock__channel", getName());
    }

    /** vergilyn mark: <br/>
     * @return "{RedissonLock.UUID}:{threadId}"
     */
    String getLockName(long threadId) {
        return id + ":" + threadId;
    }

    /* vergilyn mark:
     *  1. 重写的是: java.util.concurrent.locks.Lock
     *  2. 个人感觉(习惯)写成如下.
     * <pre>
     *      public void lock() {
     *         lock(-1, null);
     *      }
     * </pre>
     */
    @Override
    public void lock() {
        try {
            lockInterruptibly();
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }

    @Override
    public void lock(long leaseTime, TimeUnit unit) {
        try {
            lockInterruptibly(leaseTime, unit);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }

    /* vergilyn mark:
     * 与{@link RedissonLock#lock()}的区别: 该方法会把InterruptedException抛出(未调用Thread.currentThread().interrupt());
     */
    @Override
    public void lockInterruptibly() throws InterruptedException {
        lockInterruptibly(-1, null);
    }

    @Override
    public void lockInterruptibly(long leaseTime, TimeUnit unit) throws InterruptedException {
        long threadId = Thread.currentThread().getId();
        Long ttl = tryAcquire(leaseTime, unit, threadId);
        // lock acquired
        if (ttl == null) {
            return;
        }

        RFuture<RedissonLockEntry> future = subscribe(threadId);
        commandExecutor.syncSubscription(future);

        try {
            while (true) {
                ttl = tryAcquire(leaseTime, unit, threadId);
                // lock acquired
                if (ttl == null) {
                    break;
                }

                // waiting for message
                // getEntry(threadId).getLatch() -> new Semaphore(0)
                //   tryAcquire(ttl, TimeUnit.MILLISECONDS) 表示等待ttl这么久后返回.(可理解成: Thread.sleep(ttl))
                //   等待期间, 就会通过pub/sub去"唤醒"竞争获取锁.
                if (ttl >= 0) {
                    getEntry(threadId).getLatch().tryAcquire(ttl, TimeUnit.MILLISECONDS);
                } else {
                    // 因为new Semaphore(0), 所以acquire()会一直等待(正常情况 ttl 必然 >= 0); 直到被pub/sub"唤醒"当前threadId.
                    getEntry(threadId).getLatch().acquire();
                }
            }
        } finally {
            unsubscribe(future, threadId); // 方法结束(正常执行完, 或异常), 都需要取消订阅;
        }
//        get(lockAsync(leaseTime, unit)); // 被源代码注释
    }

    /** vergilyn mark: <br/>
     * 尝试获取锁
     * @param leaseTime 自动释放锁的时长
     * @param unit 时长单位
     * @param threadId 线程ID
     * @return 0/null, 成功获取锁;
     */
    private Long tryAcquire(long leaseTime, TimeUnit unit, long threadId) {
        return get(tryAcquireAsync(leaseTime, unit, threadId));
    }
    
    private RFuture<Boolean> tryAcquireOnceAsync(long leaseTime, TimeUnit unit, final long threadId) {
        if (leaseTime != -1) {
            return tryLockInnerAsync(leaseTime, unit, threadId, RedisCommands.EVAL_NULL_BOOLEAN);
        }
        RFuture<Boolean> ttlRemainingFuture = tryLockInnerAsync(commandExecutor.getConnectionManager().getCfg().getLockWatchdogTimeout(), TimeUnit.MILLISECONDS, threadId, RedisCommands.EVAL_NULL_BOOLEAN);
        ttlRemainingFuture.addListener(new FutureListener<Boolean>() {
            @Override
            public void operationComplete(Future<Boolean> future) throws Exception {
                if (!future.isSuccess()) {
                    return;
                }

                Boolean ttlRemaining = future.getNow();
                // lock acquired
                if (ttlRemaining) {
                    scheduleExpirationRenewal(threadId);
                }
            }
        });
        return ttlRemainingFuture;
    }

    /**
     * 如果<code>leaseTime == -1</code>, 会为<code>RFuture</code>添加一个Listener:
     *   如果是持有锁的线程, 会每隔(internalLockLeaseTime / 3)刷新锁的剩余有效时长为(internalLockLeaseTime).
     */
    private <T> RFuture<Long> tryAcquireAsync(long leaseTime, TimeUnit unit, final long threadId) {
        // tryLockInnerAsync被子类重写: RedissonFairLock, RedissonReadLock, RedissonWriteLock
        if (leaseTime != -1) {
            return tryLockInnerAsync(leaseTime, unit, threadId, RedisCommands.EVAL_LONG);
        }
        // 如果自动失效时长是-1, 则用默认配置: 30 * 1000 ms (后续会递归重置为 30 * 1000ms, 避免出现阻塞, 让任何情况下lock_name都会自动失效)
        RFuture<Long> ttlRemainingFuture = tryLockInnerAsync(commandExecutor.getConnectionManager().getCfg().getLockWatchdogTimeout(), TimeUnit.MILLISECONDS, threadId, RedisCommands.EVAL_LONG);
        // 为这个future添加一个指定的listener), 当这个future执行完成会立即通知listener.
        // 因为tryLockInnerAsync()内部是异步执行, 所以listener的作用: 当ttlRemainingFuture执行完(redis-lua脚本成功return), 根据RedisCommands.EVAL_LONG把redis的结果进行转换, 然后调用FutureListener.operationComplete()
        ttlRemainingFuture.addListener(new FutureListener<Long>() {
            @Override
            public void operationComplete(Future<Long> future) throws Exception {
                if (!future.isSuccess()) { // true: redis-lua脚本执行完并return
                    return;
                }

                // getNow(): 用RedisCommands.EVAL_LONG转换return的结果
                Long ttlRemaining = future.getNow();

                // lock acquired
                if (ttlRemaining == null) {  // 当ttlRemaining == null时, 表示当前线程成功获取锁;
                    // 生成TimerTask, 每隔 lockWatchdogTimeout / 3 ms, 把锁的失效时长重置回 lockWatchdogTimeout
                    scheduleExpirationRenewal(threadId);
                }
            }
        });
        return ttlRemainingFuture;
    }

    @Override
    public boolean tryLock() {
        return get(tryLockAsync());
    }

    /** vergilyn mark: <br/>
     * <p>未指定自动失效时长(leaseTime = -1, 默认赋值为{@link RedissonLock#internalLockLeaseTime}), 且持有锁的线程才会调用此方法</p>
     * <p>
     *    为持有锁的线程增加一个TimerTask: 延迟lockWatchdogTimeout / 3 ms后执行TimerTask ->
     *      重置锁的剩余时长为{@link RedissonLock#internalLockLeaseTime}, 并且递归此行为, 直到调用{@link #cancelExpirationRenewal()}
     * </p>
     * 相比不设置leaseTime或设置为无穷大, 每次重置剩余时长可以避免永久阻塞(死锁), 让任何情况下"lock_name"都会自动失效;
     * @param threadId
     * @see #cancelExpirationRenewal()
     */
    private void scheduleExpirationRenewal(final long threadId) {
        // 当存在时, 表示已存在相同"{RedissonLock.UUID}:{lock_name}"的TimerTask (防止TimerTask重复执行)
        if (expirationRenewalMap.containsKey(getEntryName())) {
            return;
        }

        Timeout task = commandExecutor.getConnectionManager().newTimeout(new TimerTask() {
            @Override
            public void run(Timeout timeout) throws Exception {
                
                RFuture<Boolean> future = commandExecutor.evalWriteAsync(getName(), LongCodec.INSTANCE, RedisCommands.EVAL_BOOLEAN,
                        // 保证当前线程是持有锁的线程, 且未失效. 则把锁的剩余有效时长重置回: internalLockLeaseTime
                        "if (redis.call('hexists', KEYS[1], ARGV[2]) == 1) then " +
                            "redis.call('pexpire', KEYS[1], ARGV[1]); " +
                            "return 1; " +
                        "end; " +
                        "return 0;",
                          Collections.<Object>singletonList(getName()), internalLockLeaseTime, getLockName(threadId));
                
                future.addListener(new FutureListener<Boolean>() {
                    @Override
                    public void operationComplete(Future<Boolean> future) throws Exception {
                        expirationRenewalMap.remove(getEntryName()); // 从标记map中移除后, 之后可以再生成TimerTask
                        if (!future.isSuccess()) {
                            log.error("Can't update lock " + getName() + " expiration", future.cause());
                            return;
                        }

                        // getNow()结果: RedisCommands.EVAL_BOOLEAN
                        // true: 当前线程即持有锁的线程, 且之前重置失效时长成功, 则可以递归重置锁的剩余时长.
                        // false: 当前线程不是持有锁的线程, 或当前线程持有的锁已失效.
                        if (future.getNow()) {
                            // reschedule itself
                            scheduleExpirationRenewal(threadId);
                        }
                    }
                });
            }
        }, internalLockLeaseTime / 3, TimeUnit.MILLISECONDS);

        // 任务调度代码, 感觉理解了, 但又感觉没理解...
        if (expirationRenewalMap.putIfAbsent(getEntryName(), task) != null) {
            task.cancel();
        }
    }

    /** vergilyn mark: <br/>
     * 取消到期更新, 对应: {@link #scheduleExpirationRenewal(long)}. 调用unlockXX()时调用此方法. <br/>
     * 因为, 当leaseTime = -1时, 会生成一个TimerTsk去刷新锁的剩余时长(递归此过程), 所以当unlockXX()时要显示取消这一过程.
     */
    void cancelExpirationRenewal() {
        Timeout task = expirationRenewalMap.remove(getEntryName());
        if (task != null) {
            task.cancel();
        }
    }

    /** vergilyn mark: <br/>
     * 尝试获取锁,且内部执行是异步的(执行Lua脚本是异步)
     * @param leaseTime 锁的自动失效时长
     * @param unit leaseTime的单位
     * @param threadId
     * @param command 转换Lua返回结果
     * @return
     */
    <T> RFuture<T> tryLockInnerAsync(long leaseTime, TimeUnit unit, long threadId, RedisStrictCommand<T> command) {
        internalLockLeaseTime = unit.toMillis(leaseTime);
        // KEYS[1]: "{lock_name}"   ARGV[1]: {leaseTime} (ms)   ARGV[2]: "{RedissonLock.UUID}:{threadId}"
        return commandExecutor.evalWriteAsync(getName(), LongCodec.INSTANCE, command,
                  // 锁未被任何线程获取, 这当前线程成功获取锁
                  "if (redis.call('exists', KEYS[1]) == 0) then " +
                      "redis.call('hset', KEYS[1], ARGV[2], 1); " +  // 标记锁"lock_name"已被线程"{RedissonLock.UUID}:{threadId}"获取1次
                      "redis.call('pexpire', KEYS[1], ARGV[1]); " +  // 设置自动失效时长
                      "return nil; " + // 返回nil, 表示当前线程成功获取锁
                  "end; " +
                  // (重复获取锁) 当前线程即持有锁的线程, 即可重入锁(Reentrant Lock)
                  "if (redis.call('hexists', KEYS[1], ARGV[2]) == 1) then " +
                      "redis.call('hincrby', KEYS[1], ARGV[2], 1); " + // 获取次数递增, 必须相应的释放这么多次锁
                      "redis.call('pexpire', KEYS[1], ARGV[1]); " + // 每次获取锁都重置失效时长
                      "return nil; " +
                  "end; " +
                  "return redis.call('pttl', KEYS[1]);", // 锁已被其他线程获取, 返回锁的剩余有效时长
                    Collections.<Object>singletonList(getName()),
                    internalLockLeaseTime, getLockName(threadId));
    }

    /** vergilyn mark: <br/>
     * 获取锁失败调用(比如获取锁超时), 不同锁实现不一样. <br/>
     * ex, RedissonFairLock, 获取锁失败的线程, 需要从缓存:"redisson_lock_queue:{lock_name}"、"redisson_lock_timeout:{lock_name}"中移除
     */
    private void acquireFailed(long threadId) {
        get(acquireFailedAsync(threadId));
    }

    /** vergilyn mark: <br/>
     * <p>如果是一般的锁(RedissonLock), 获取锁失败的线程不需要做任何事情.<p/>
     * <p>如果是公平锁(RedissonFairLock), 获取锁失败的线程, 需要从"redisson_lock_queue:{lock_name}"、"redisson_lock_timeout:{lock_name}"中移除
     * <p/>
     */
    protected RFuture<Void> acquireFailedAsync(long threadId) {
        return newSucceededFuture(null); // TODO vergilyn: 未理解代码含义
    }

    /* vergilyn mark: <br/>
     * 其实内部还是异步的, 因为执行Lua的方法都是: tryLockInnerAsync()
     * @param waitTime 最大获取锁的等待时长
     * @param leaseTime 自动释放锁的时长
     * @param unit waitTime、leaseTime的单位
     * @return true, 成功获取锁;
     */
    @Override
    public boolean tryLock(long waitTime, long leaseTime, TimeUnit unit) throws InterruptedException {
        long time = unit.toMillis(waitTime);
        long current = System.currentTimeMillis();
        final long threadId = Thread.currentThread().getId();
        Long ttl = tryAcquire(leaseTime, unit, threadId);
        // lock acquired
        if (ttl == null) {
            return true;
        }
        
        time -= (System.currentTimeMillis() - current);
        if (time <= 0) { // 超过获取锁的最大等待时长
            // 不同锁实现不一样, 比如FairLock, 当获取锁超时后, 需要从"redisson_lock_queue:{lock_name}"、"redisson_lock_timeout:{lock_name}"中移除
            acquireFailed(threadId);
            return false;
        }
        
        current = System.currentTimeMillis();
        final RFuture<RedissonLockEntry> subscribeFuture = subscribe(threadId);
        if (!await(subscribeFuture, time, TimeUnit.MILLISECONDS)) {
            if (!subscribeFuture.cancel(false)) {
                subscribeFuture.addListener(new FutureListener<RedissonLockEntry>() {
                    @Override
                    public void operationComplete(Future<RedissonLockEntry> future) throws Exception {
                        if (subscribeFuture.isSuccess()) {
                            unsubscribe(subscribeFuture, threadId);
                        }
                    }
                });
            }
            acquireFailed(threadId);
            return false;
        }

        try {
            time -= (System.currentTimeMillis() - current);
            if (time <= 0) {
                acquireFailed(threadId);
                return false;
            }
        
            while (true) {
                long currentTime = System.currentTimeMillis();
                ttl = tryAcquire(leaseTime, unit, threadId);
                // lock acquired
                if (ttl == null) {
                    return true;
                }

                time -= (System.currentTimeMillis() - currentTime);
                if (time <= 0) {
                    acquireFailed(threadId);
                    return false;
                }

                // waiting for message
                currentTime = System.currentTimeMillis();
                if (ttl >= 0 && ttl < time) {
                    getEntry(threadId).getLatch().tryAcquire(ttl, TimeUnit.MILLISECONDS);
                } else {
                    getEntry(threadId).getLatch().tryAcquire(time, TimeUnit.MILLISECONDS);
                }

                time -= (System.currentTimeMillis() - currentTime);
                if (time <= 0) {
                    acquireFailed(threadId);
                    return false;
                }
            }
        } finally {
            unsubscribe(subscribeFuture, threadId);
        }
//        return get(tryLockAsync(waitTime, leaseTime, unit));
    }

    protected RedissonLockEntry getEntry(long threadId) {
        return PUBSUB.getEntry(getEntryName());
    }

    protected RFuture<RedissonLockEntry> subscribe(long threadId) {
        return PUBSUB.subscribe(getEntryName(), getChannelName(), commandExecutor.getConnectionManager());
    }

    protected void unsubscribe(RFuture<RedissonLockEntry> future, long threadId) {
        PUBSUB.unsubscribe(future.getNow(), getEntryName(), getChannelName(), commandExecutor.getConnectionManager());
    }

    @Override
    public boolean tryLock(long waitTime, TimeUnit unit) throws InterruptedException {
        return tryLock(waitTime, -1, unit);
    }

    @Override
    public void unlock() {
        Boolean opStatus = get(unlockInnerAsync(Thread.currentThread().getId()));
        if (opStatus == null) {
            throw new IllegalMonitorStateException("attempt to unlock lock, not locked by current thread by node id: "
                    + id + " thread-id: " + Thread.currentThread().getId());
        }
        if (opStatus) {
            cancelExpirationRenewal();
        }

//        Future<Void> future = unlockAsync();
//        future.awaitUninterruptibly();
//        if (future.isSuccess()) {
//            return;
//        }
//        if (future.cause() instanceof IllegalMonitorStateException) {
//            throw (IllegalMonitorStateException)future.cause();
//        }
//        throw commandExecutor.convertException(future);
    }

    @Override
    public Condition newCondition() {
        // TODO implement
        throw new UnsupportedOperationException();
    }

    @Override
    public void forceUnlock() {
        get(forceUnlockAsync());
    }

    @Override
    public RFuture<Boolean> forceUnlockAsync() {
        cancelExpirationRenewal();
        return commandExecutor.evalWriteAsync(getName(), LongCodec.INSTANCE, RedisCommands.EVAL_BOOLEAN,
                "if (redis.call('del', KEYS[1]) == 1) then "
                + "redis.call('publish', KEYS[2], ARGV[1]); "
                + "return 1 "
                + "else "
                + "return 0 "
                + "end",
                Arrays.<Object>asList(getName(), getChannelName()), LockPubSub.unlockMessage);
    }

    @Override
    public boolean isLocked() {
        return isExists();
    }

    @Override
    public RFuture<Boolean> isExistsAsync() {
        return commandExecutor.writeAsync(getName(), codec, RedisCommands.EXISTS, getName());
    }

    @Override
    public boolean isHeldByCurrentThread() {
        RFuture<Boolean> future = commandExecutor.writeAsync(getName(), LongCodec.INSTANCE, RedisCommands.HEXISTS, getName(), getLockName(Thread.currentThread().getId()));
        return get(future);
    }

    @Override
    public int getHoldCount() {
        RFuture<Long> future = commandExecutor.writeAsync(getName(), LongCodec.INSTANCE, RedisCommands.HGET, getName(), getLockName(Thread.currentThread().getId()));
        Long res = get(future);
        if (res == null) {
            return 0;
        }
        return res.intValue();
    }

    @Override
    public RFuture<Boolean> deleteAsync() {
        return forceUnlockAsync();
    }

    @Override
    public RFuture<Void> unlockAsync() {
        long threadId = Thread.currentThread().getId();
        return unlockAsync(threadId);
    }

    /** vergilyn mark: <br/>
     * 解锁, 内部执行Lua是异步.
     * @return true, 成功解锁; false, 当前线程持有锁, 但获取锁次数 > 解锁次数; null, 锁存在, 但不是当前线程持有, 当前线程无法解锁. 或其他情况;
     */
    protected RFuture<Boolean> unlockInnerAsync(long threadId) {
        return commandExecutor.evalWriteAsync(getName(), LongCodec.INSTANCE, RedisCommands.EVAL_BOOLEAN,
                // PUBLISH channel message: 将信息(message)发送到指定的频道(channel)
                // KEYS[1]: "lock_name"
                // KEYS[2]: "redisson_lock__channel:{lock_name}"
                // ARGV[1]: 0L
                // ARGV[2]: internalLockLeaseTime
                // ARGV[3]: "{RedissonLock.UUID}:{threadId}"
                "if (redis.call('exists', KEYS[1]) == 0) then " +
                    "redis.call('publish', KEYS[2], ARGV[1]); " + // 不存在锁, 将信息"0"发送到频道"redisson_lock__channel:{lock_name}"
                    "return 1; " + // 解锁成功
                "end;" +

                "if (redis.call('hexists', KEYS[1], ARGV[3]) == 0) then " +
                    "return nil;" + // 无法解锁: 存在锁, 但不是当前线程持有, 当前线程无法解锁.
                "end; " +

                "local counter = redis.call('hincrby', KEYS[1], ARGV[3], -1); " + // 得到获取线程获取锁的次数, 必须释放相应多的次数
                "if (counter > 0) then " +
                    "redis.call('pexpire', KEYS[1], ARGV[2]); " + // 重置锁的有效时长
                    "return 0; " +
                "else " + // 释放了相应次数, 则可执行解锁
                    "redis.call('del', KEYS[1]); " + // 解锁
                    "redis.call('publish', KEYS[2], ARGV[1]); " + // 发送通知
                    "return 1; "+ // 解锁成功
                "end; " +
                "return nil;",
                Arrays.<Object>asList(getName(), getChannelName()), LockPubSub.unlockMessage, internalLockLeaseTime, getLockName(threadId));

    }

    /** vergilyn mark: <br/>
     * TODO 未体会到 {@link #unlockInnerAsync(long)}与{@link #unlockAsync(long)}的具体区别.
     * 个人猜测:
     *   {@link #unlockInnerAsync(long)}的<code>innerAsync</code>指的是执行Lua脚本是异步的, 所以都会通过{@link #get(RFuture)}来获取Lua脚本的结果. <BR/>
     *   {@link #unlockAsync(long)}的<code>async</code>指这个解锁操作是异步的, 所以才需要为RFuture添加Listener. <BR/>
     * 但不理解, 这些async有什么用?
     */
    @Override
    public RFuture<Void> unlockAsync(final long threadId) {
        final RPromise<Void> result = newPromise();
        RFuture<Boolean> future = unlockInnerAsync(threadId);

        // 因为unlockInnerAsync()是异步, 所以需要添加Listener监听完成响应.
        future.addListener(new FutureListener<Boolean>() {
            @Override
            public void operationComplete(Future<Boolean> future) throws Exception {
                if (!future.isSuccess()) { // 若Future执行失败
                    result.tryFailure(future.cause()); //TODO 不理解: 作用、代码调用
                    return;
                }

                // Future结果:
                //   true, 成功解锁;
                //   false, 当前线程持有锁, 但获取锁次数 > 解锁次数;
                //   null, 锁存在, 但不是当前线程持有, 当前线程无法解锁. 或其他情况;
                Boolean opStatus = future.getNow();
                if (opStatus == null) {
                    IllegalMonitorStateException cause = new IllegalMonitorStateException("attempt to unlock lock, not locked by current thread by node id: "
                            + id + " thread-id: " + threadId);
                    result.tryFailure(cause);
                    return;
                }
                if (opStatus) {
                    cancelExpirationRenewal();
                }
                result.trySuccess(null);
            }
        });

        return result;
    }

    @Override
    public RFuture<Void> lockAsync() {
        return lockAsync(-1, null);
    }

    @Override
    public RFuture<Void> lockAsync(long leaseTime, TimeUnit unit) {
        final long currentThreadId = Thread.currentThread().getId();
        return lockAsync(leaseTime, unit, currentThreadId);
    }

    @Override
    public RFuture<Void> lockAsync(long currentThreadId) {
        return lockAsync(-1, null, currentThreadId);
    }
    
    @Override
    public RFuture<Void> lockAsync(final long leaseTime, final TimeUnit unit, final long currentThreadId) {
        final RPromise<Void> result = newPromise();
        RFuture<Long> ttlFuture = tryAcquireAsync(leaseTime, unit, currentThreadId);
        ttlFuture.addListener(new FutureListener<Long>() {
            @Override
            public void operationComplete(Future<Long> future) throws Exception {
                if (!future.isSuccess()) {
                    result.tryFailure(future.cause());
                    return;
                }

                Long ttl = future.getNow();

                // lock acquired
                if (ttl == null) {
                    if (!result.trySuccess(null)) {
                        unlockAsync(currentThreadId);
                    }
                    return;
                }

                final RFuture<RedissonLockEntry> subscribeFuture = subscribe(currentThreadId);
                subscribeFuture.addListener(new FutureListener<RedissonLockEntry>() {
                    @Override
                    public void operationComplete(Future<RedissonLockEntry> future) throws Exception {
                        if (!future.isSuccess()) {
                            result.tryFailure(future.cause());
                            return;
                        }

                        lockAsync(leaseTime, unit, subscribeFuture, result, currentThreadId);
                    }

                });
            }
        });

        return result;
    }

    private void lockAsync(final long leaseTime, final TimeUnit unit,
            final RFuture<RedissonLockEntry> subscribeFuture, final RPromise<Void> result, final long currentThreadId) {
        RFuture<Long> ttlFuture = tryAcquireAsync(leaseTime, unit, currentThreadId);
        ttlFuture.addListener(new FutureListener<Long>() {
            @Override
            public void operationComplete(Future<Long> future) throws Exception {
                if (!future.isSuccess()) {
                    unsubscribe(subscribeFuture, currentThreadId);
                    result.tryFailure(future.cause());
                    return;
                }

                Long ttl = future.getNow();
                // lock acquired
                if (ttl == null) {
                    unsubscribe(subscribeFuture, currentThreadId);
                    if (!result.trySuccess(null)) {
                        unlockAsync(currentThreadId);
                    }
                    return;
                }

                // waiting for message
                final RedissonLockEntry entry = getEntry(currentThreadId);
                synchronized (entry) {
                    if (entry.getLatch().tryAcquire()) {
                        lockAsync(leaseTime, unit, subscribeFuture, result, currentThreadId);
                    } else {
                        final AtomicReference<Timeout> futureRef = new AtomicReference<Timeout>();
                        final Runnable listener = new Runnable() {
                            @Override
                            public void run() {
                                if (futureRef.get() != null) {
                                    futureRef.get().cancel();
                                }
                                lockAsync(leaseTime, unit, subscribeFuture, result, currentThreadId);
                            }
                        };

                        entry.addListener(listener);

                        if (ttl >= 0) {
                            Timeout scheduledFuture = commandExecutor.getConnectionManager().newTimeout(new TimerTask() {
                                @Override
                                public void run(Timeout timeout) throws Exception {
                                    synchronized (entry) {
                                        if (entry.removeListener(listener)) {
                                            lockAsync(leaseTime, unit, subscribeFuture, result, currentThreadId);
                                        }
                                    }
                                }
                            }, ttl, TimeUnit.MILLISECONDS);
                            futureRef.set(scheduledFuture);
                        }
                    }
                }
            }
        });
    }

    @Override
    public RFuture<Boolean> tryLockAsync() {
        return tryLockAsync(Thread.currentThread().getId());
    }

    @Override
    public RFuture<Boolean> tryLockAsync(long threadId) {
        return tryAcquireOnceAsync(-1, null, threadId);
    }

    @Override
    public RFuture<Boolean> tryLockAsync(long waitTime, TimeUnit unit) {
        return tryLockAsync(waitTime, -1, unit);
    }

    @Override
    public RFuture<Boolean> tryLockAsync(long waitTime, long leaseTime, TimeUnit unit) {
        long currentThreadId = Thread.currentThread().getId();
        return tryLockAsync(waitTime, leaseTime, unit, currentThreadId);
    }

    @Override
    public RFuture<Boolean> tryLockAsync(final long waitTime, final long leaseTime, final TimeUnit unit,
            final long currentThreadId) {
        final RPromise<Boolean> result = newPromise();

        final AtomicLong time = new AtomicLong(unit.toMillis(waitTime));
        final long currentTime = System.currentTimeMillis();
        RFuture<Long> ttlFuture = tryAcquireAsync(leaseTime, unit, currentThreadId);
        ttlFuture.addListener(new FutureListener<Long>() {
            @Override
            public void operationComplete(Future<Long> future) throws Exception {
                if (!future.isSuccess()) {
                    result.tryFailure(future.cause());
                    return;
                }

                Long ttl = future.getNow();

                // lock acquired
                if (ttl == null) {
                    if (!result.trySuccess(true)) {
                        unlockAsync(currentThreadId);
                    }
                    return;
                }

                long elapsed = System.currentTimeMillis() - currentTime;
                time.addAndGet(-elapsed);
                
                if (time.get() <= 0) {
                    trySuccessFalse(currentThreadId, result);
                    return;
                }
                
                final long current = System.currentTimeMillis();
                final AtomicReference<Timeout> futureRef = new AtomicReference<Timeout>();
                final RFuture<RedissonLockEntry> subscribeFuture = subscribe(currentThreadId);
                subscribeFuture.addListener(new FutureListener<RedissonLockEntry>() {
                    @Override
                    public void operationComplete(Future<RedissonLockEntry> future) throws Exception {
                        if (!future.isSuccess()) {
                            result.tryFailure(future.cause());
                            return;
                        }

                        if (futureRef.get() != null) {
                            futureRef.get().cancel();
                        }

                        long elapsed = System.currentTimeMillis() - current;
                        time.addAndGet(-elapsed);
                        
                        tryLockAsync(time, leaseTime, unit, subscribeFuture, result, currentThreadId);
                    }
                });
                if (!subscribeFuture.isDone()) {
                    Timeout scheduledFuture = commandExecutor.getConnectionManager().newTimeout(new TimerTask() {
                        @Override
                        public void run(Timeout timeout) throws Exception {
                            if (!subscribeFuture.isDone()) {
                                subscribeFuture.cancel(false);
                                trySuccessFalse(currentThreadId, result);
                            }
                        }
                    }, time.get(), TimeUnit.MILLISECONDS);
                    futureRef.set(scheduledFuture);
                }
            }

        });


        return result;
    }

    private void trySuccessFalse(final long currentThreadId, final RPromise<Boolean> result) {
        acquireFailedAsync(currentThreadId).addListener(new FutureListener<Void>() {
            @Override
            public void operationComplete(Future<Void> future) throws Exception {
                if (future.isSuccess()) {
                    result.trySuccess(false);
                } else {
                    result.tryFailure(future.cause());
                }
            }
        });
    }

    private void tryLockAsync(final AtomicLong time, final long leaseTime, final TimeUnit unit,
            final RFuture<RedissonLockEntry> subscribeFuture, final RPromise<Boolean> result, final long currentThreadId) {
        if (result.isDone()) {
            unsubscribe(subscribeFuture, currentThreadId);
            return;
        }
        
        if (time.get() <= 0) {
            unsubscribe(subscribeFuture, currentThreadId);
            trySuccessFalse(currentThreadId, result);
            return;
        }
        
        final long current = System.currentTimeMillis();
        RFuture<Long> ttlFuture = tryAcquireAsync(leaseTime, unit, currentThreadId);
        ttlFuture.addListener(new FutureListener<Long>() {
            @Override
            public void operationComplete(Future<Long> future) throws Exception {
                if (!future.isSuccess()) {
                    unsubscribe(subscribeFuture, currentThreadId);
                    result.tryFailure(future.cause());
                    return;
                }

                Long ttl = future.getNow();
                // lock acquired
                if (ttl == null) {
                    unsubscribe(subscribeFuture, currentThreadId);
                    if (!result.trySuccess(true)) {
                        unlockAsync(currentThreadId);
                    }
                    return;
                }
                
                long elapsed = System.currentTimeMillis() - current;
                time.addAndGet(-elapsed);
                
                if (time.get() <= 0) {
                    unsubscribe(subscribeFuture, currentThreadId);
                    trySuccessFalse(currentThreadId, result);
                    return;
                }

                // waiting for message
                final long current = System.currentTimeMillis();
                final RedissonLockEntry entry = getEntry(currentThreadId);
                synchronized (entry) {
                    if (entry.getLatch().tryAcquire()) {
                        tryLockAsync(time, leaseTime, unit, subscribeFuture, result, currentThreadId);
                    } else {
                        final AtomicBoolean executed = new AtomicBoolean();
                        final AtomicReference<Timeout> futureRef = new AtomicReference<Timeout>();
                        final Runnable listener = new Runnable() {
                            @Override
                            public void run() {
                                executed.set(true);
                                if (futureRef.get() != null) {
                                    futureRef.get().cancel();
                                }

                                long elapsed = System.currentTimeMillis() - current;
                                time.addAndGet(-elapsed);
                                
                                tryLockAsync(time, leaseTime, unit, subscribeFuture, result, currentThreadId);
                            }
                        };
                        entry.addListener(listener);

                        long t = time.get();
                        if (ttl >= 0 && ttl < time.get()) {
                            t = ttl;
                        }
                        if (!executed.get()) {
                            Timeout scheduledFuture = commandExecutor.getConnectionManager().newTimeout(new TimerTask() {
                                @Override
                                public void run(Timeout timeout) throws Exception {
                                    synchronized (entry) {
                                        if (entry.removeListener(listener)) {
                                            long elapsed = System.currentTimeMillis() - current;
                                            time.addAndGet(-elapsed);
                                            
                                            tryLockAsync(time, leaseTime, unit, subscribeFuture, result, currentThreadId);
                                        }
                                    }
                                }
                            }, t, TimeUnit.MILLISECONDS);
                            futureRef.set(scheduledFuture);
                        }
                    }
                }
            }
        });
    }


}
;