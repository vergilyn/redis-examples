package com.vergilyn.examples.lock.fair;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

import org.redisson.Redisson;
import org.redisson.api.RedissonClient;
import org.redisson.config.Config;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.CommandLineRunner;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.ComponentScan;
import org.springframework.core.io.ClassPathResource;

/**
 * @author VergiLyn
 * @blog http://www.cnblogs.com/VergiLyn/
 * @date 2017/12/17
 */
@SpringBootApplication
@ComponentScan(basePackages = {"com.vergilyn.examples.annotation","com.vergilyn.examples.lock.fair"})
public class FairLockApplication implements CommandLineRunner{
    private static final Logger logger = LoggerFactory.getLogger(FairLockApplication.class);

    @Autowired
    FairLockTest fairLockTest;

    @Bean
    public RedissonClient redissonClient() throws IOException {
        ClassPathResource classPathResource = new ClassPathResource("config/single-server-config.json");
        Config config = Config.fromJSON(classPathResource.getFile());
        return Redisson.create(config);
    }

    public static void main(String[] args) {
        SpringApplication application = new SpringApplication(FairLockApplication.class);
        application.run(args);
    }

    @Override
    public void run(String... args) throws Exception {
        logger.info("run() >>>> ");
        List<Thread> threads = new ArrayList<>();
        for (int i = 0; i < 2; i++) {
            threads.add(new Thread(() -> {
                String s = fairLockTest.fairLock("10086");
                logger.info(Thread.currentThread().getId() + " result: " + s);
            }));
        }

        for (Thread t : threads) {
            t.start();
        }
    }
}
