package com.vergilyn.examples.redisson.template.client;


import java.util.concurrent.TimeUnit;

import javax.annotation.Resource;

import com.vergilyn.examples.redisson.template.AbstractRedisson;

import org.redisson.RedissonFairLock;
import org.redisson.api.RLock;
import org.redisson.api.RedissonClient;
import org.springframework.stereotype.Component;

/**
 * 公平锁: 先发起竞争锁的线程优先获得锁;
 */
@Component
public class FairLockClient extends AbstractRedisson {
    @Resource
    RedissonClient redissonClient;

    @Override
    public RLock newInstance(String lock) {
        return redissonClient.getFairLock(lock);
    }

}
