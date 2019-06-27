package com.vergilyn.examples;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.test.context.testng.AbstractTestNGSpringContextTests;
import redis.clients.jedis.JedisPool;

/**
 * @author VergiLyn
 * @date 2019-06-24
 */
@SpringBootTest(classes = SpringDataRedisApplication.class, properties = {"spring.profiles.active=datasource"})
public class BasicTestng extends AbstractTestNGSpringContextTests {
    @Autowired
    protected StringRedisTemplate stringRedisTemplate;
    @Autowired
    protected JedisPool jedisPool;
}
