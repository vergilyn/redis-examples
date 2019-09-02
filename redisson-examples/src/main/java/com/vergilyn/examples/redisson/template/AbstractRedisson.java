package com.vergilyn.examples.redisson.template;

import java.util.concurrent.TimeUnit;
import java.util.function.Supplier;

import com.vergilyn.examples.redisson.exception.RedissonException;

import org.redisson.api.RLock;

/**
 * 备注:
 * <p>
 *   leaseTime(default): 30 * 1000 ms, see: {@link org.redisson.config.Config#lockWatchdogTimeout};<br/>
 *   waitTime(default): -1, 无限等待获取锁;(expired = 30s, 但会每隔10s重置为expired=30s, 直到调用unlockXX() )<br/>
 * </p>
 * <p>
 *   {@link RLock#isLocked()}: true, "lock"已被某个线程获取;<br/>
 *   {@link RLock#isHeldByCurrentThread()}: true, 当前线程持有"lock";<br/>
 * </p>
 * <p>
 *   {@link RLock#unlock()}: 释放"lock", 只有持有锁的线程才能成功释放锁;<br/>
 *   {@link RLock#forceUnlock()}:  强制释放锁, 任何竞争相同lock的线程都可以释放锁;<br/>
 * </p>
 *
 * 更多参考: <a href="https://github.com/redisson/redisson/wiki/8.-分布式锁和同步器">分布式锁和同步器</a>
 */
public abstract class AbstractRedisson {

    public abstract RLock newInstance(String lock);

    public final <R> R tryTemplate(String lock, long waitTime, long leaseTime, TimeUnit unit, Supplier<R> method) {
        RLock rLock = null;
        try {
            rLock = newInstance(lock);
            boolean isLock = rLock.tryLock(waitTime, leaseTime, unit);
            if (!isLock) {
                throw new RedissonException("Get RedissonLock Fail: " + lock);
            }
            R result = method.get();
            rLock.unlock();

            return result;
        } catch (RedissonException | InterruptedException e) {
            throw new RedissonException(e);
        } catch (Exception e) {
            throw e;
        } finally {
            if (rLock != null && rLock.isHeldByCurrentThread()) {
                rLock.unlock();
            }
        }
    }

    public final <R> R template(String lock, long leaseTime, TimeUnit unit, Supplier<R> method) {
        RLock rLock = newInstance(lock);
        rLock.lock(leaseTime, unit);
        R rs = method.get();
        rLock.unlock();
        return rs;
    }

}
