package com.vergilyn.examples.redisson;

import org.redisson.Redisson;
import org.redisson.api.RedissonClient;
import org.redisson.config.Config;

public class RedissonClientFactory {
	public static final String DEFAULT_HOST = "127.0.0.1";
	public static final int DEFAULT_PORT = 56379;

	private static final Config _config;

	static {
		_config = new Config();
		_config.useSingleServer()
				.setAddress("redis://" + DEFAULT_HOST + ":" + DEFAULT_PORT)
				.setDatabase(0);
	}

	public static RedissonClient createRedissonClient(){
		return Redisson.create(_config);
	}
}
