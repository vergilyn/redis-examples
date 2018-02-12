package com.vergilyn.examples.junit.redisson.jdk.concurrent;

import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

import com.vergilyn.examples.junit.redisson.BaseTest;

import org.junit.Test;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * CountDownLatch :
 * <ul>
 *     <li>{@link CountDownLatch#await()}</li>
 *     <li>{@link CountDownLatch#await(long, TimeUnit)}</li>
 *     <li>{@link CountDownLatch#countDown()}</li>
 *     <li>{@link CountDownLatch#getCount()}</li>
 * </ul>
 * like, {@link java.util.concurrent.CyclicBarrier}
 *
 * <a href="http://www.cnblogs.com/dolphin0520/p/3920397.html">Java并发编程：CountDownLatch、CyclicBarrier和Semaphore</a>
 * @author VergiLyn
 * @blog http://www.cnblogs.com/VergiLyn/
 * @date 2018/1/6
 */
public class CountDownLatchTest extends BaseTest{

    /**
     * 验证: If the current count is zero then this method returns immediately.(The count reaches zero due to invocations of the {@link CountDownLatch#countDown} method)
     * <br/> count < threads, 计数小于线程数: 假设count=2, 线程数=3.
     */
    @Test
    public void awaitReturnForCountZero(){
        logger.info("main: run..." );
        final CountDownLatch latch = new CountDownLatch(2);

        new ThreadCountZero(latch, 2).start();
        new ThreadCountZero(latch, 4).start();
        new ThreadCountZero(latch, 8).start();

        logger.info("main: wait Thread finish." );
        try {
            latch.await();
//          latch.await(long, TimeUnit) // 超时后立即返回, 如果count == 0, 返回true; 否则, 返回false. (若未超时count == 0, 立即返回true)
            logger.info("main: wait end.");
        } catch (InterruptedException e) {
            e.printStackTrace();
        }
    }

    /**
     * 验证: Some other thread {@linkplain Thread#interrupt interrupts} the current thread(current thread指的是调用await()方法的线程, ).
     * {@link CountDownLatch#await()} or {@link CountDownLatch#await(long, TimeUnit)} returns immediately.
     * If the current thread:
     * <ul>
     *  <li>has its interrupted status set on entry to this method;(在进入该方法await()时已经设置了中断状态;)
     *  <li>or is {@linkplain Thread#interrupt interrupted} while waiting, (或, 在等待的时候线程"被中断")
     * </ul>
     * then {@link InterruptedException} is thrown and the current thread's interrupted status is cleared.
     * <br/>
     */
    @Test
    public void awaitThreadInterrupt(){
        logger.info("main: run..." );
        Thread currentThread = Thread.currentThread(); // current thread指此线程, 并不是ThreadCountZero.
        final CountDownLatch latch = new CountDownLatch(2);

        new ThreadCountZero(latch, 2).start();
        new ThreadCountZero(latch, 8).start();

        logger.info("main: wait Thread finish." );
        try {
            currentThread.interrupt(); // 进入await()方法前设置为"中断"状态, 则调用await()会立即抛出InterruptedException
            latch.await();
            logger.info("main: wait end.");
        } catch (InterruptedException e) {
            logger.info("exception msg: " , e);
        }
    }

    /**
     * 验证: is {@linkplain Thread#interrupt interrupted} while waiting. (在等待的时候线程"被中断", 被中断的是调用await()的线程)
     */
    @Test
    public void awaitThreadInterrupt2(){
        logger.info("main: run..." );
        Thread mainThread = Thread.currentThread();
        final CountDownLatch latch = new CountDownLatch(2);

        // 如果id = 2, 会延迟1000ms调用 mainThread.interrupt(), 即"Some other thread interrupts the current thread".
        // 此时await()会立即返回, 抛出InterruptedException.
        // 注意看执行时间,
        new ThreadInterrupt(latch, 2, 1, mainThread).start();
        new ThreadInterrupt(latch, 8, 2, mainThread).start();

        logger.info("main: wait Thread finish." );
        try {
            latch.await();
            logger.info("main: wait end.");
        } catch (InterruptedException e) {
            logger.info("exception msg: " , e);
        }
    }

    /**
     * {@link CountDownLatch#await()}、{@link CountDownLatch#await(long, TimeUnit)} 区别:
     *   多一个超时机制. 若未超时, count达到0, 会立即返回true; 若已超时, 且count > 0则立即返回false,否则立即返回true.
     */
    public void awaitTimeout(){

    }

    /**
     * Decrements the count of the latch, releasing all waiting threads if the count reaches zero.
     * <br/> 减少锁存器的计数, 释放所有等待的线程, 如果计数达到0.
     * <p>If the current count is greater than zero then it is decremented.(如果当前计数大于0, 则递减.)
     * If the new count is zero then all waiting threads are re-enabled for thread scheduling purposes.
     * (如果新计数为0, 则所有正在等待的线程都将重新启用以进行线程调度. 即await()会返回并结束线程的阻塞, 执行之后的代码)
     *
     * <p>If the current count equals zero then nothing happens. (如果当前计数等于0, 则不会发生任何事. 不会变成-1)
     */
    public void countDown() { }

    /**
     * Returns the current count.
     *
     * <p>This method is typically used for debugging and testing purposes.
     * (此方法通常用于调试和测试目的.)
     */
    public void getCount() { }
}

class ThreadCountZero extends Thread{
    private final static Logger logger = LoggerFactory.getLogger(ThreadCountZero.class);

    private final CountDownLatch latch;
    private final long sleep;

    public ThreadCountZero(CountDownLatch latch, long sleep) {
        this.latch = latch;
        this.sleep = sleep;
    }

    @Override
    public void run() {
        Long threadId = Thread.currentThread().getId();
        try {
            logger.info("Thread: " + threadId + " run. sleep: " + sleep);
            Thread.sleep(sleep * 1000);
        } catch (InterruptedException e) {
            e.printStackTrace();
        } finally {
            latch.countDown();
            logger.info("Thread: " + threadId + " end. count: " + latch.getCount());
        }
    }
}

class ThreadInterrupt extends Thread{
    private final static Logger logger = LoggerFactory.getLogger(ThreadCountZero.class);

    private final CountDownLatch latch;
    private final long sleep;
    private final int id;
    private final Thread mainThread;


    public ThreadInterrupt(CountDownLatch latch, long sleep, int id, Thread mainThread) {
        this.latch = latch;
        this.sleep = sleep;
        this.id = id;
        this.mainThread = mainThread;
    }

    @Override
    public void run() {
        try {
            logger.info("Thread: " + id + " run. sleep: " + sleep);
            if(id == 2){
                Thread.sleep(1000);
                logger.info("mainThread.interrupt()!");
                mainThread.interrupt(); // "中断"调用await()的线程. 使await()立即返回, 会抛出InterruptedException
//              Thread.currentThread().interrupt(); // 并不是中断当前子线程
            }
            Thread.sleep(sleep * 1000);
        } catch (InterruptedException e) {
            e.printStackTrace();
        } finally {
            latch.countDown();
            logger.info("Thread: " + id + " end. count: " + latch.getCount());
        }
    }
}