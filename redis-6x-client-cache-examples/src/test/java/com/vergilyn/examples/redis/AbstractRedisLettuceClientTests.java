package com.vergilyn.examples.redis;

import com.vergilyn.examples.commons.redis.LettuceRedisClientFactory;

import io.lettuce.core.api.StatefulRedisConnection;
import io.lettuce.core.api.sync.RedisCommands;

/**
 * @author vergilyn
 * @since 2021-08-27
 */
public abstract class AbstractRedisLettuceClientTests {

	protected LettuceRedisClientFactory lettuceRedisClientFactory = LettuceRedisClientFactory.getInstance("127.0.0.1", 16379);

	protected RedisCommands syncRedisCommand() throws Exception {
		return lettuceRedisClientFactory.syncRedisCommand();
	}

	protected StatefulRedisConnection<String, String> statefulRedisConnection() throws Exception {
		return lettuceRedisClientFactory.statefulRedisConnection();
	}
}
