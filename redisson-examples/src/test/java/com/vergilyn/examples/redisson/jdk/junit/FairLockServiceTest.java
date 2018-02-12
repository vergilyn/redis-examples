package com.vergilyn.examples.redisson.jdk.junit;

import java.util.concurrent.CountDownLatch;

import javax.annotation.Resource;

import com.vergilyn.examples.redisson.RedissonApplication;
import com.vergilyn.examples.redisson.annotation.RedissonLockAnno;
import com.vergilyn.examples.redisson.annotation.RedissonLockKey;
import com.vergilyn.examples.redisson.annotation.RedissonLockType;
import com.vergilyn.examples.redisson.service.FairLockService;

import org.junit.Test;
import org.junit.runner.RunWith;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.junit4.SpringRunner;

/**
 * @author VergiLyn
 * @blog http://www.cnblogs.com/VergiLyn/
 * @date 2018/2/6
 */
@RunWith(SpringRunner.class)
@SpringBootTest(classes= RedissonApplication.class)
public class FairLockServiceTest {
    private static final Logger logger = LoggerFactory.getLogger(FairLockServiceTest.class);

    @Resource
    FairLockService fairLockService;

    @Test
    public void fairLockTest() throws Exception {
        logger.info("fairLockTest() >>>> begin");

        int count = 4;
        CountDownLatch latch = new CountDownLatch(count);

        for (int i = 0; i < count; i++) {
            new Thread(() -> {
                try {
                    String s = fairLockService.fairLock("10086");
                    logger.info(Thread.currentThread().getId() + " result: " + s);
                } catch (Exception e){
                    logger.error(e.getMessage());
                } finally {
                    latch.countDown();
                }
            }).start();
        }

        logger.info("fairLockTest() >>>> await....");
        latch.await();

        logger.info("fairLockTest() >>>> end....");

    }

    // 注解形式参考
    @RedissonLockAnno(prefix="test", type = RedissonLockType.BASIC_LOCK)
    public void TestAnno(@RedissonLockKey Long index, @RedissonLockKey(fields={"id", "username"}) Object obj){
    }
}
