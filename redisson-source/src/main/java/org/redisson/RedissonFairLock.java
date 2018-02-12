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
import java.util.UUID;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.locks.Condition;

import org.redisson.api.RFuture;
import org.redisson.api.RLock;
import org.redisson.client.codec.LongCodec;
import org.redisson.client.protocol.RedisCommands;
import org.redisson.client.protocol.RedisStrictCommand;
import org.redisson.command.CommandExecutor;
import org.redisson.pubsub.LockPubSub;

/**
 * Distributed implementation of {@link java.util.concurrent.locks.Lock}
 * Implements reentrant lock.<br>
 * Lock will be removed automatically if template disconnects.
 * <p>
 * Implements a <b>fair</b> locking so it guarantees an acquire order by threads.
 *
 * @author Nikita Koksharov
 *
 */
/** vergilyn mark: <br/>
 * redisson用Lua实现的好处: 1. 减少了大量redis连接; 2. 将部分内存的占用和逻辑交给了redis服务器, 减轻了部署服务器的压力; 3. 因为redis的脚本特性, 执行逻辑效率会比程序高 <br/>
 * "redisson_lock_queue:{lock_name}": 这LIST的顺序就是获取锁的顺序, 即FairLock实现原理. <br/>
 * "redisson_lock_timeout:{xxx}": 这其中的score并不表示获取锁的顺序, 而是表示线程竞争锁的失效时间点. <br/>
 * remove stale threads: 为什么不写成公共的? <br/>
 * redisson是如何解决服务器之间时间不同步的: 因为{RedissonLock.UUID}, 不同服务器生成UUID不同, 所以其时间戳的值肯定会来至原有的服务器. <br/>
 */
public class RedissonFairLock extends RedissonLock implements RLock {

    /** vergilyn mark: <br/>
     * 竞争锁的线程的等待有效时长默认增量; <br/>
     * 当线程加入竞争锁线程的等待队列时("redisson_lock_queue:{lock_name}"), 在时间点: <code>currentTime + ttl(当前锁的剩余有效时长) + threadWaitTime<code/> 之前可以有效的竞争获取锁("redisson_lock_timeout:{lock_name}").
     */
    private final long threadWaitTime = 5000;
    private final CommandExecutor commandExecutor;

    /** vergilyn mark: <br/>
     *  构造函数, 被protected修饰, 说明不能在外部通过new获取; 一般的构建是:{@link Redisson#getFairLock(String)}. <br/>
     * @param name lock的'锁名', 实际定义在{@link RedissonObject#name}
     * @param id
     */
    protected RedissonFairLock(CommandExecutor commandExecutor, String name, UUID id) {
        super(commandExecutor, name, id);
        this.commandExecutor = commandExecutor;
    }

    /** vergilyn mark: <br/>
     * LIST的顺序即获取锁的顺序, 即FairLock(公平锁)逻辑 <br/>
     * type : LIST, rpush <br/>
     * key  : "redisson_lock_queue:{lock_name}" <br/>
     * value: "{RedissonLock.UUID}:{threadId}" <br/>
     * @return ex, redisson_lock_queue:{lock_name}
     */
    String getThreadsQueueName() {
        return prefixName("redisson_lock_queue", getName());
    }

    /** vergilyn mark: <br/>
     * type : SORT-SET <br/>
     * key  : "redisson_lock_timeout:{lock_name}" <br/>
     * value: "{RedissonLock.UUID}:{threadId}" <br/>
     * score(timeout): 时间戳ms, 每次会校验并移除过期的竞争锁的线程. 当 score < currentTime, 表示竞争锁的该线程已失效. <br/>
     * @return  ex, redisson_lock_timeout:{lock_name}
     */
    String getTimeoutSetName() {
        return prefixName("redisson_lock_timeout", getName());
    }
    
    @Override
    protected RedissonLockEntry getEntry(long threadId) {
        return PUBSUB.getEntry(getEntryName() + ":" + threadId);
    }

    /** vergilyn mark: <br/>
     * 订阅, override: {@link RedissonLock#subscribe(long)}
     */
    @Override
    protected RFuture<RedissonLockEntry> subscribe(long threadId) {
        return PUBSUB.subscribe(getEntryName() + ":" + threadId, 
                getChannelName() + ":" + getLockName(threadId), commandExecutor.getConnectionManager());
    }

    /** vergilyn mark: <br/>
     * 取消订阅, override: {@link RedissonLock#unsubscribe(RFuture, long)}
     */
    @Override
    protected void unsubscribe(RFuture<RedissonLockEntry> future, long threadId) {
        PUBSUB.unsubscribe(future.getNow(), getEntryName() + ":" + threadId, 
                getChannelName() + ":" + getLockName(threadId), commandExecutor.getConnectionManager());
    }

    /* vergilyn mark: 当获取锁失败时候后调用 */
    @Override
    protected RFuture<Void> acquireFailedAsync(long threadId) {
        /* vergilyn mark: lua中数组下标是从1开始
         * lindex key index                   : LIST, 返回列表中下标为指定索引值的元素. 如果指定索引值不在列表的区间范围内, 返回 nil.
         * zrange key start stop [WITHSCORES] : SORT-SET, 通过索引区间返回有序集合成指定区间内的成员.
         * zincrby key increment member       : SORT-SET, 有序集合中对指定成员的分数加上增量 increment.
         * zrem key member [member ...]       : SORT-SET, 移除有序集合中的一个或多个成员
         * lrem key count value               : LIST, 移除列表元素(count=0, 表示全部)
         */
        return commandExecutor.evalWriteAsync(getName(), LongCodec.INSTANCE, RedisCommands.EVAL_VOID,
                    // KEYS[1]: "redisson_lock_queue:{xxx}",   ARGV[1]: "{RedissonLock.UUID}:{threadId}"
                    // KEYS[2]: "redisson_lock_timeout:{xxx}", ARGV[2]: "{threadWaitTime}"(默认: 5000ms)
                    "local firstThreadId = redis.call('lindex', KEYS[1], 0); " +
                    "if firstThreadId == ARGV[1] then " + // 如果, 当前线程是FairLock队列的第一个线程
                        "local keys = redis.call('zrange', KEYS[2], 0, -1); " +
                        "for i = 1, #keys, 1 do " + 
                            "redis.call('zincrby', KEYS[2], -tonumber(ARGV[2]), keys[i]);" + // 则把竞争相同锁的所有"redisson_lock_timeout:{xxx}"队列中的score(timeout时间戳)都减少"{threadWaitTime}"
                                // 1. 为什么要减少"{threadWaitTime}"?
                                //   先要明白"redisson_lock_timeout:{xxx}的值是时间戳, 表示该线程在这个时间点之前才可以有效的竞争获取锁. 否则, 会检测移除这些过期的竞争锁的线程.
                                //   然后, 每个竞争锁的线程的"redisson_lock_timeout:{xxx}"值(score/timeout) = "{currentTime + threadWaitTime}", 见下面tryLockInnerAsync(long, TimeUnit,long, RedisStrictCommand<T>)代码
                                     //   当获取锁失败的线程是队列中的第一个线程时, 意味着这个"threadWaitTime"是多余的, 队列之后的所有线程失效时间点都要提前"threadWaitTime"这么多ms.(ps: 感叹, 大佬写的代码逻辑就是严谨.)

                                // 2. 为什么只有获取锁失败的线程是队列中的第一个线程才需要减少"{threadWaitTime}"? (以下解释并未完全说服自己, 可能只是我想多了, 但应该没有理解错)
                                //   FairLock的特性是获取锁有序, 所以调用本方法一定是也是有序的, 即每次都是"redisson_lock_queue:{xxx}"队列中的第一个才会调用本方法(即每次都会执行IF代码)
                                //   ex, 代码逻辑见RedissonLock#tryLocktryLock(long waitTime, long leaseTime, TimeUnit unit), 正常情况应该是竞争同一个"lock_name"的waitTime&leaseTime都是一样的.
                                //     那么调用acquireFailedAsync()方法的线程一定会是"redisson_lock_queue:{xxx}"队列中的第一个.
                                //     若, waitTime不一样(比如某个id的waitTime奇葩的设置的比别的小), 才会出现不是"redisson_lock_queue:{xxx}"队列中的第一个调用.(这就是以上解释为什么未说服我)
                        "end;" +
                    "end;" +
                    // 把获取锁失败的线程从 "redisson_lock_queue:{xxx}"、"redisson_lock_timeout:{xxx}"中移除
                    "redis.call('zrem', KEYS[2], ARGV[1]); " +
                    "redis.call('lrem', KEYS[1], 0, ARGV[1]); ",
                    Arrays.<Object>asList(getThreadsQueueName(), getTimeoutSetName()),  // KEYS
                    getLockName(threadId), threadWaitTime);                        // PARAMS
    }

    /** vergilyn mark: <br/>
     * FairLock核心方法, 尝试获取锁(内部异步获取);
     * remark: 因为{RedissonLock.UUID}, 解决了服务器之间的时间不同步
     * @param leaseTime 锁自动释放的时长
     * @param unit leaseTime的时间单位
     * @param threadId 线程
     */
    @Override
    <T> RFuture<T> tryLockInnerAsync(long leaseTime, TimeUnit unit, long threadId, RedisStrictCommand<T> command) {
        internalLockLeaseTime = unit.toMillis(leaseTime);

        long currentTime = System.currentTimeMillis();
        if (command == RedisCommands.EVAL_NULL_BOOLEAN) {
            return commandExecutor.evalWriteAsync(getName(), LongCodec.INSTANCE, command,
                    // remove stale threads: 移除过期的竞争锁的线程
                    // KEYS[1]: "lock_name",                    ARGV[1]: "{leaseTime}"
                    // KEYS[2]: "redisson_lock_queue:{xxx}",    ARGV[2]: "{RedissonLock.UUID}:{threadId}"
                    // KEYS[3]: "redisson_lock_timeout:{xxx}",  ARGV[3]: "{currentTime}" (部署服务器的时间ms, 不是redis-server的服务器时间)
                    "while true do "
                    + "local firstThreadId2 = redis.call('lindex', KEYS[2], 0);"
                    + "if firstThreadId2 == false then "
                        + "break;"
                    + "end; "
                    + "local timeout = tonumber(redis.call('zscore', KEYS[3], firstThreadId2));"
                    + "if timeout <= tonumber(ARGV[3]) then "
                        + "redis.call('zrem', KEYS[3], firstThreadId2); "
                        + "redis.call('lpop', KEYS[2]); "
                    + "else "
                        + "break;"
                    + "end; "
                  + "end;"
                    + 
                    
                    "if (redis.call('exists', KEYS[1]) == 0) and ((redis.call('exists', KEYS[2]) == 0) "
                            + "or (redis.call('lindex', KEYS[2], 0) == ARGV[2])) then " +
                            "redis.call('lpop', KEYS[2]); " +
                            "redis.call('zrem', KEYS[3], ARGV[2]); " +
                            "redis.call('hset', KEYS[1], ARGV[2], 1); " +
                            "redis.call('pexpire', KEYS[1], ARGV[1]); " +
                            "return nil; " +
                        "end; " +
                        "if (redis.call('hexists', KEYS[1], ARGV[2]) == 1) then " +
                            "redis.call('hincrby', KEYS[1], ARGV[2], 1); " +
                            "redis.call('pexpire', KEYS[1], ARGV[1]); " +
                            "return nil; " +
                        "end; " +
                        "return 1;", 
                    Arrays.<Object>asList(getName(), getThreadsQueueName(), getTimeoutSetName()),  // KEYS
                    internalLockLeaseTime, getLockName(threadId), currentTime);               // PARAMS
        }
        
        if (command == RedisCommands.EVAL_LONG) {
            return commandExecutor.evalWriteAsync(getName(), LongCodec.INSTANCE, command,
                    // remove stale threads: 移除过期的竞争锁的线程
                    // KEYS[1]: "lock_name",                    ARGV[1]: "{leaseTime}"
                    // KEYS[2]: "redisson_lock_queue:{xxx}",    ARGV[2]: "{RedissonLock.UUID}:{threadId}"
                    // KEYS[3]: "redisson_lock_timeout:{xxx}",  ARGV[3]: "{currentTime + threadWaitTime}" (部署服务器的时间ms, 不是redis-server的服务器时间)
                    //                                          ARGV[4]: "{currentTime}"

                    // 移除竞争lock_name中无效的线程
                    "while true do "
                    + "local firstThreadId2 = redis.call('lindex', KEYS[2], 0);"
                    + "if firstThreadId2 == false then "
                        + "break;"
                    + "end; "
                    + "local timeout = tonumber(redis.call('zscore', KEYS[3], firstThreadId2));"
                    + "if timeout <= tonumber(ARGV[4]) then " // 如果存在且已失效, 则相应的从"redisson_lock_queue:{xxx}"、"redisson_lock_timeout:{xxx}"中移除
                        + "redis.call('zrem', KEYS[3], firstThreadId2); "
                        + "redis.call('lpop', KEYS[2]); "
                    + "else "
                        + "break;"
                    + "end; "
                  + "end;"

                      // exists: 若 key 存在返回 1 ，否则返回 0 。
                        // 1. KEYS[1]: 记录获取到lock_name的线程, 及记录线程获取锁的次数. (此HASH只会存在一个field)
                        //    type: HASH, key: "lock_name", field: "{RedissonLock.UUID}:{threadId}", value: 1 (value表示线程获取锁的次数, 释放锁时必须释放相同的次数, 才会释放锁lock_name)
                        // 2. 当不存在"redisson_lock_queue:{lock_name}"时, 表示没有竞争对手存在; 或, 竞争队列的第一个值为当前线程 (因为是FairLock, 所以要满足 redis.call('lindex', KEYS[2], 0) == ARGV[2])
                      + "if (redis.call('exists', KEYS[1]) == 0) and ((redis.call('exists', KEYS[2]) == 0) "
                            + "or (redis.call('lindex', KEYS[2], 0) == ARGV[2])) then " +
                            // 当前线程获取到锁, 从queue、timeout中移除
                            "redis.call('lpop', KEYS[2]); " +
                            "redis.call('zrem', KEYS[3], ARGV[2]); " +
                            "redis.call('hset', KEYS[1], ARGV[2], 1); " + // 标记当前锁已被某个线程获取
                            "redis.call('pexpire', KEYS[1], ARGV[1]); " + // 设置标记的失效时常, 默认是 30 * 1000 ms
                            "return nil; " +
                        "end; " +

                        // (重复获取锁) 当前线程即持有锁的线程, 即可重入锁(Reentrant Lock)
                        "if (redis.call('hexists', KEYS[1], ARGV[2]) == 1) then " +
                            "redis.call('hincrby', KEYS[1], ARGV[2], 1); " + // 记录线程获取锁的次数
                            "redis.call('pexpire', KEYS[1], ARGV[1]); " +    // 重置失效时长
                            "return nil; " +
                        "end; " +
                            
                        "local firstThreadId = redis.call('lindex', KEYS[2], 0); " +
                        "local ttl; " + 
                        "if firstThreadId ~= false and firstThreadId ~= ARGV[2] then " + // 队列存在, 且队列的第一个值 ≠ 当前线程
                            "ttl = tonumber(redis.call('zscore', KEYS[3], firstThreadId)) - tonumber(ARGV[4]);" + 
                        "else "
                          + "ttl = redis.call('pttl', KEYS[1]);" + 
                        "end; " + 
                            
                        "local timeout = ttl + tonumber(ARGV[3]);" + // 计算竞争线程的失效时间点
                        "if redis.call('zadd', KEYS[3], timeout, ARGV[2]) == 1 then " + // 设置当前线程竞争锁的失效时间点
                            "redis.call('rpush', KEYS[2], ARGV[2]);" + // 将当前线程加入竞争锁的队列中
                        "end; " +
                        "return ttl;", // 返回持有锁的剩余时长,
                        Arrays.<Object>asList(getName(), getThreadsQueueName(), getTimeoutSetName()), 
                                    internalLockLeaseTime, getLockName(threadId), currentTime + threadWaitTime, currentTime);
        }
        
        throw new IllegalArgumentException();
    }

    /** vergilyn mark: <br/>
     * 释放锁(内部异步); 只有持有锁的线程才能释放锁, 且线程获取多少次锁, 就要释放多少次, 否则不会释放(除非锁达到失效时长).
     * lua执行返回: 1, 成功释放锁; nil/0, 未释放任何锁.
     * @see RedissonFairLock#forceUnlockAsync()
     */
    @Override
    protected RFuture<Boolean> unlockInnerAsync(long threadId) {
        return commandExecutor.evalWriteAsync(getName(), LongCodec.INSTANCE, RedisCommands.EVAL_BOOLEAN,
                // remove stale threads: 移除无效的获取锁的线程
                "while true do "
                + "local firstThreadId2 = redis.call('lindex', KEYS[2], 0);"
                + "if firstThreadId2 == false then "
                    + "break;"
                + "end; "
                + "local timeout = tonumber(redis.call('zscore', KEYS[3], firstThreadId2));"
                + "if timeout <= tonumber(ARGV[4]) then "
                    + "redis.call('zrem', KEYS[3], firstThreadId2); "
                    + "redis.call('lpop', KEYS[2]); "
                + "else "
                    + "break;"
                + "end; "
              + "end;"

                // 锁已释放(KEYS[1]已自动失效), 通知下一个获取锁的线程
              + "if (redis.call('exists', KEYS[1]) == 0) then " + 
                    "local nextThreadId = redis.call('lindex', KEYS[2], 0); " + 
                    "if nextThreadId ~= false then " +
                        "redis.call('publish', KEYS[4] .. ':' .. nextThreadId, ARGV[1]); " +
                    "end; " +
                    "return 1; " + // 成功释放锁
                "end;" +

                // 当前线程不是持有锁的线程, 不允许释放锁
                "if (redis.call('hexists', KEYS[1], ARGV[3]) == 0) then " +
                    "return nil;" +
                "end; " +

                // 当前线程是持有锁的线程, value: 递减
                // (ReentrantLock概念) 当同一线程中多次获取锁, 必须释放相同多的次数, 才会最终释放锁
                "local counter = redis.call('hincrby', KEYS[1], ARGV[3], -1); " +
                "if (counter > 0) then " +
                    "redis.call('pexpire', KEYS[1], ARGV[2]); " + // 每次递减后重置失效时长
                    "return 0; " + // 释放锁失败
                "end; " +

                // 当前线程所有获取锁的地方都释放了锁, 则正确的释放锁
                "redis.call('del', KEYS[1]); " +

                // 通知下一个竞争相同锁的线程.
                "local nextThreadId = redis.call('lindex', KEYS[2], 0); " + 
                "if nextThreadId ~= false then " +
                    "redis.call('publish', KEYS[4] .. ':' .. nextThreadId, ARGV[1]); " +
                "end; " +
                "return 1; ", // 成功释放锁
                Arrays.<Object>asList(getName(), getThreadsQueueName(), getTimeoutSetName(), getChannelName()), 
                LockPubSub.unlockMessage, internalLockLeaseTime, getLockName(threadId), System.currentTimeMillis());
    }
    
    @Override
    public Condition newCondition() {
        throw new UnsupportedOperationException();
    }

    @Override
    public RFuture<Boolean> deleteAsync() {
        return commandExecutor.writeAsync(getName(), RedisCommands.DEL_OBJECTS, getName(), getThreadsQueueName(), getTimeoutSetName());
    }

    /** vergilyn mark: <br/>
     * 强制释放锁;
     * @see RedissonLock#unlockInnerAsync
     * @see #unlock()
     */
    @Override
    public RFuture<Boolean> forceUnlockAsync() {
        cancelExpirationRenewal();
        return commandExecutor.evalWriteAsync(getName(), LongCodec.INSTANCE, RedisCommands.EVAL_BOOLEAN,
                // remove stale threads
                "while true do "
                + "local firstThreadId2 = redis.call('lindex', KEYS[2], 0);"
                + "if firstThreadId2 == false then "
                    + "break;"
                + "end; "
                + "local timeout = tonumber(redis.call('zscore', KEYS[3], firstThreadId2));"
                + "if timeout <= tonumber(ARGV[2]) then "
                    + "redis.call('zrem', KEYS[3], firstThreadId2); "
                    + "redis.call('lpop', KEYS[2]); "
                + "else "
                    + "break;"
                + "end; "
              + "end;"
                + 

                // 强制释放锁, 即使当前线程不是持有锁的线程, 并通知下一个竞争相同锁的线程
                "if (redis.call('del', KEYS[1]) == 1) then " + 
                    "local nextThreadId = redis.call('lindex', KEYS[2], 0); " + 
                    "if nextThreadId ~= false then " +
                        "redis.call('publish', KEYS[4] .. ':' .. nextThreadId, ARGV[1]); " +
                    "end; " + 
                    "return 1; " + // 强制释放锁成功
                "end; " + 
                "return 0;", // 强制释放锁失败
                Arrays.<Object>asList(getName(), getThreadsQueueName(), getTimeoutSetName(), getChannelName()), 
                LockPubSub.unlockMessage, System.currentTimeMillis());
    }

}
