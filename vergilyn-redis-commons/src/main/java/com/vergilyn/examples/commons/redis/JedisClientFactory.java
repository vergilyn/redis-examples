package com.vergilyn.examples.commons.redis;

import redis.clients.jedis.Jedis;
import redis.clients.jedis.JedisPool;

public class JedisClientFactory extends AbstractRedisClient{

	private static JedisClientFactory instance;
	private final JedisPool jedisPool;

	private JedisClientFactory(String host, int port){
		this.jedisPool = new JedisPool(poolConfig(), host, port);
	}

	public static JedisClientFactory getInstance() {
		return getInstance(DEFAULT_HOST, DEFAULT_PORT);
	}
	public static JedisClientFactory getInstance(String host, int port) {
		if (instance != null){
			return instance;
		}

		synchronized (RedisClientFactory.class) {
			if (instance != null) {
				return instance;
			}

			instance = new JedisClientFactory(host, port);
			return instance;
		}
	}

	/**
	 *
	 * @see Jedis#close() 将connection返回 pool
	 * @see Jedis#disconnect() 关闭socket
	 */
	public Jedis jedis() {
		return jedisPool.getResource();
	}
}
