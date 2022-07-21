package com.vergilyn.examples.data.redisson;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.data.redis.core.StringRedisTemplate;

/**
 * @author vergilyn
 * @since 2022-07-08
 */
@SpringBootTest(classes = RedissonDataApplication.class)
public abstract class AbstractRedissonDataApplicationTests {
    @Autowired
    protected StringRedisTemplate _stringRedisTemplate;

}
