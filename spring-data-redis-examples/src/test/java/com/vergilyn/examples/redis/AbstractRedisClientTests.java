package com.vergilyn.examples.redis;

import com.vergilyn.examples.commons.redis.JedisClientFactory;
import com.vergilyn.examples.commons.redis.RedisClientFactory;

import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.data.redis.core.StringRedisTemplate;
import redis.clients.jedis.Jedis;

/**
 * @author vergilyn
 * @since 2021-04-30
 */
public abstract class AbstractRedisClientTests {
	private static final JedisClientFactory jedisClientFactory = JedisClientFactory.getInstance();
	private static final RedisClientFactory redisClientFactory = RedisClientFactory.getInstance();

	protected final StringRedisTemplate _stringRedisTemplate = redisClientFactory.stringRedisTemplate();
	protected final RedisTemplate<Object, Object> _redisTemplate = redisClientFactory.redisTemplate();

	/**
	 * @see Jedis#close() 将connection返回 pool
	 * @see Jedis#disconnect() 关闭socket
	 */
	protected static Jedis jedis(){
		return jedisClientFactory.jedis();
	}

	protected StringRedisTemplate stringRedisTemplate(){
		return this._stringRedisTemplate;
	}

	protected <K, V> RedisTemplate<K, V> redisTemplate(){
		return (RedisTemplate<K, V>) _redisTemplate;
	}

	protected void enableTransaction(){
		redisClientFactory.enableTransaction();
	}

}
