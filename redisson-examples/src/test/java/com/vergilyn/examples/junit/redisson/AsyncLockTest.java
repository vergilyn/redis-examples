package com.vergilyn.examples.junit.redisson;


import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Semaphore;
import java.util.concurrent.TimeUnit;

import javax.annotation.Resource;

import com.vergilyn.examples.junit.BaseTest;

import org.junit.Test;
import org.redisson.api.RFuture;
import org.redisson.api.RLock;
import org.redisson.api.RedissonClient;

public class AsyncLockTest extends BaseTest {
    public static final String LOCK_NAME = "test";
    /** 不需要lock的code执行时间; 单位: s; */
    public static final Long UN_TIMEOUT = 2L;
    /** 需要lock的code执行时间; 单位: s; */
    public static final Long TIMEOUT = 3L;

    private final int THREAD_COUNT = 1;

    @Resource
    private RedissonClient redissonClient;

    /**
     * 可能干扰因素: 第一次可能会慢一些;
     *   个人理解: async普遍效率更快,  async节约的时间可以理解成是  redisson内部方法的时间
     */
    @Test
    public void average(){
        int count = 4;
        int i = count;
        long begin = System.currentTimeMillis();
        while (i > 0){
            async();
            i --;
        }

        System.out.println("sync average: " + (System.currentTimeMillis() - begin) / count);

        i = count;
        begin = System.currentTimeMillis();
        while (i > 0){
            sync();
            i --;
        }

        System.out.println("async average: " + (System.currentTimeMillis() - begin) / count);

    }

    @Test
    public void sync(){
        long begin = System.currentTimeMillis();
        CountDownLatch latch = new CountDownLatch(THREAD_COUNT);

        for (int i = 0; i < THREAD_COUNT; i++){
            new SyncThread(redissonClient, latch).start();
        }

        try {
            latch.await();
            System.out.println("sync 耗时(ms): " + (System.currentTimeMillis() - begin));
        } catch (InterruptedException e) {
            e.printStackTrace();
        }
    }

    @Test
    public void async(){
        long begin = System.currentTimeMillis();
        CountDownLatch latch = new CountDownLatch(THREAD_COUNT);
        for (int i = 0; i < THREAD_COUNT; i++){
            new AsyncThread(redissonClient, latch).start();
        }

        try {
            latch.await();
            System.out.println("async 耗时(ms): " + (System.currentTimeMillis() - begin));
        } catch (InterruptedException e) {
            e.printStackTrace();
        }
    }

    @Test
    public void error(){
        long begin = System.currentTimeMillis();
        CountDownLatch latch = new CountDownLatch(THREAD_COUNT);
        for (int i = 0; i < THREAD_COUNT; i++){
            new ErrorThread(redissonClient, latch).start();
        }

        try {
            latch.await();
            System.out.println("error 耗时(ms): " + (System.currentTimeMillis() - begin));
        } catch (InterruptedException e) {
            e.printStackTrace();
        }
    }

}


class SyncThread extends Thread {
    private RedissonClient redissonClient;
    private CountDownLatch latch;
    private Semaphore semaphore;

    public SyncThread(RedissonClient redissonClient, CountDownLatch latch){
        this.redissonClient = redissonClient;
        this.latch = latch;
        this.semaphore = new Semaphore(0);
    }

    @Override
    public void run() {
        RLock lock = null;
        try {
            // 1. 不需要lock的code
            semaphore.tryAcquire(AsyncLockTest.UN_TIMEOUT, TimeUnit.SECONDS);  // -> 等价于 Thread.sleep(UN_TIMEOUT * 1000)

            // 2.
            lock = redissonClient.getLock(AsyncLockTest.LOCK_NAME);
            lock.lock();

            // 3. 需要lock的code
            semaphore.tryAcquire(AsyncLockTest.TIMEOUT, TimeUnit.SECONDS);

        } catch (InterruptedException e) {
            e.printStackTrace();
        } finally {
            if(lock != null && lock.isHeldByCurrentThread()){
                lock.unlock();
            }
            latch.countDown();
        }
    }
}

class AsyncThread extends Thread {
    private RedissonClient redissonClient;
    private CountDownLatch latch;
    private Semaphore semaphore;

    public AsyncThread(RedissonClient redissonClient, CountDownLatch latch){
        this.redissonClient = redissonClient;
        this.latch = latch;
        this.semaphore = new Semaphore(0);
    }

    @Override
    public void run() {
        RLock lock = null;
        try {
            // 1. 先异步获取锁
            lock = redissonClient.getLock(AsyncLockTest.LOCK_NAME);
            RFuture<Void> lockAsync = lock.lockAsync();

            // 2. 不需要lock的code
            semaphore.tryAcquire(AsyncLockTest.UN_TIMEOUT, TimeUnit.SECONDS);  // -> 等价于 Thread.sleep(UN_TIMEOUT * 1000)

            lockAsync.await();  // 阻塞, 直到获取锁

            // 3. 需要lock的code
            semaphore.tryAcquire(AsyncLockTest.TIMEOUT, TimeUnit.SECONDS);

        } catch (InterruptedException e) {
            e.printStackTrace();
        } finally {
            if(lock != null && lock.isHeldByCurrentThread()){
                lock.unlock();
            }
            latch.countDown();
        }
    }
}

/**
 * 不需要lock的code在锁内执行, 效率一定低.
 */
class ErrorThread extends Thread {
    private RedissonClient redissonClient;
    private CountDownLatch latch;
    private Semaphore semaphore;

    public ErrorThread(RedissonClient redissonClient, CountDownLatch latch){
        this.redissonClient = redissonClient;
        this.latch = latch;
        this.semaphore = new Semaphore(0);
    }

    @Override
    public void run() {
        RLock lock = null;
        try {
            // 1.
            lock = redissonClient.getLock(AsyncLockTest.LOCK_NAME);
            lock.lock();

            // 2. 不需要lock的code
            semaphore.tryAcquire(AsyncLockTest.UN_TIMEOUT, TimeUnit.SECONDS);  // -> 等价于 Thread.sleep(UN_TIMEOUT * 1000)

            // 3. 需要lock的code
            semaphore.tryAcquire(AsyncLockTest.TIMEOUT, TimeUnit.SECONDS);

        } catch (InterruptedException e) {
            e.printStackTrace();
        } finally {
            if(lock != null && lock.isHeldByCurrentThread()){
                lock.unlock();
            }
            latch.countDown();
        }
    }
}