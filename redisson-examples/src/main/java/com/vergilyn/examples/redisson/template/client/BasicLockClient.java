package com.vergilyn.examples.redisson.template.client;

import java.util.concurrent.TimeUnit;

import javax.annotation.Resource;

import com.vergilyn.examples.redisson.template.AbstractRedisson;

import org.redisson.RedissonLock;
import org.redisson.api.RLock;
import org.redisson.api.RedissonClient;
import org.springframework.stereotype.Component;

/**
 * 一般锁
 */
@Component
public class BasicLockClient extends AbstractRedisson {
    @Resource
    RedissonClient redissonClient;

    @Override
    public RLock newInstance(String lock) {
        return redissonClient.getLock(lock);
    }

}
