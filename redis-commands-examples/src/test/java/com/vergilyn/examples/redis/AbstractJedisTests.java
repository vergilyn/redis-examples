package com.vergilyn.examples.redis;

import com.vergilyn.examples.commons.redis.JedisClientFactory;

import redis.clients.jedis.Jedis;

/**
 * @author vergilyn
 * @since 2021-04-30
 */
public abstract class AbstractJedisTests {

	protected static final JedisClientFactory jedisClientFactory = JedisClientFactory.getInstance();

	public AbstractJedisTests() {
	}

	/**
	 * @see Jedis#close() 将connection返回 pool
	 * @see Jedis#disconnect() 关闭socket
	 */
	protected static Jedis jedis(){
		return jedisClientFactory.jedis();
	}
}
