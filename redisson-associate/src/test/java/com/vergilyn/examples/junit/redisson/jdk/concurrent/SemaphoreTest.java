package com.vergilyn.examples.junit.redisson.jdk.concurrent;

import java.util.concurrent.Semaphore;

import com.vergilyn.examples.junit.redisson.BaseTest;

import org.junit.Test;

/**
 * @author VergiLyn
 * @blog http://www.cnblogs.com/VergiLyn/
 * @date 2018/1/6
 */
public class SemaphoreTest extends BaseTest{

    @Test
    public void test(){
        Semaphore semaphore = new Semaphore(0);

        System.out.println("semaphore init: new Semaphore(0)");

        // bug: 因为是permits = 0, release()一次后 变成permits = -1;
        //   此时调用 acquire() = true, 因为 permits = -1 + 1 = 0, permits >= 0 返回true;
        semaphore.release();

        System.out.println("semaphore.tryAcquire(): " + semaphore.tryAcquire());

        System.out.println("semaphore end... drainPermits: " + semaphore.drainPermits() );

    }

    @Test
    public void test2(){
        Semaphore semaphore = new Semaphore(0);

        try {
            System.out.println("semaphore.tryAcquire(): " + semaphore.tryAcquire());
        } catch (Exception e) {
            e.printStackTrace();
        }
        System.out.println("semaphore end...");
    }

    @Test
    public void test3(){
        Semaphore semaphore = new Semaphore(0);

        try {
            semaphore.acquire(); // 因为 permits = 0, 会一直等待获取
            System.out.println("semaphore.acquire()... ");
        } catch (InterruptedException e) {
            e.printStackTrace();
        }
        System.out.println("semaphore end...");
    }
}
