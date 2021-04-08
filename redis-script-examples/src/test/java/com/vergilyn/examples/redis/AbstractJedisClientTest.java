package com.vergilyn.examples.redis;

import com.vergilyn.examples.redis.redis.RedisClientFactory;

import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.data.redis.core.StringRedisTemplate;

public abstract class AbstractJedisClientTest {
	protected final StringRedisTemplate _stringRedisTemplate = RedisClientFactory.getInstance().stringRedisTemplate();
	protected final RedisTemplate<Object, Object> _redisTemplate = RedisClientFactory.getInstance().redisTemplate();

}
