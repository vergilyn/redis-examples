package com.vergilyn.examples.junit.redisson.netty.timer;

import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

import com.vergilyn.examples.junit.redisson.BaseTest;

import io.netty.util.HashedWheelTimer;
import io.netty.util.Timeout;
import io.netty.util.TimerTask;
import org.junit.Test;

/**
 * @author VergiLyn
 * @blog http://www.cnblogs.com/VergiLyn/
 * @date 2018/1/6
 */
public class TimerTest extends BaseTest {

    @Test
    public void test(){
        CountDownLatch latch = new CountDownLatch(10);
        TimerTask timerTask = new TimerTask() {
            @Override
            public void run(Timeout timeout) throws Exception {
                logger.info("timer task running...");
                latch.countDown();
            }
        };

        HashedWheelTimer timer = new HashedWheelTimer();
        // 延迟 1s 执行 1次 TimerTask.
        timer.newTimeout(timerTask, 1, TimeUnit.SECONDS);

        try {
            logger.info("timer task end...");
            latch.await();
        } catch (InterruptedException e) {
            e.printStackTrace();
        }
    }
}
