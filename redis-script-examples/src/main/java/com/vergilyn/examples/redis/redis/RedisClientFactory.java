package com.vergilyn.examples.redis.redis;

import org.apache.commons.pool2.impl.GenericObjectPoolConfig;
import org.springframework.data.redis.connection.RedisConnectionFactory;
import org.springframework.data.redis.connection.RedisStandaloneConfiguration;
import org.springframework.data.redis.connection.jedis.JedisClientConfiguration;
import org.springframework.data.redis.connection.jedis.JedisConnectionFactory;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.data.redis.core.StringRedisTemplate;

@SuppressWarnings("ALL")
public class RedisClientFactory {
	public static final String HOST = "127.0.0.1";
	public static final int PORT = 56379;

	private final RedisTemplate<Object, Object> _redisTemplate;
	private final StringRedisTemplate _stringRedisTemplate;

	private static final RedisClientFactory instance = new RedisClientFactory();

	private RedisClientFactory() {
		RedisConnectionFactory redisConnectionFactory = buildRedisConnectionFactory();

		this._redisTemplate = new RedisTemplate<>();
		this._redisTemplate.setConnectionFactory(redisConnectionFactory);
		this._redisTemplate.afterPropertiesSet();

		this._stringRedisTemplate = new StringRedisTemplate();
		this._stringRedisTemplate.setConnectionFactory(redisConnectionFactory);
		this._stringRedisTemplate.afterPropertiesSet();
	}

	/**
	 * @see JedisConnectionConfiguration#createJedisConnectionFactory(org.springframework.beans.factory.ObjectProvider)
	 */
	private RedisConnectionFactory buildRedisConnectionFactory() {
		GenericObjectPoolConfig poolConfig = new GenericObjectPoolConfig();
		poolConfig.setMaxTotal(64);
		poolConfig.setMinIdle(0);
		poolConfig.setMaxIdle(8);

		JedisClientConfiguration.JedisClientConfigurationBuilder builder = JedisClientConfiguration.builder();
		builder.usePooling().poolConfig(poolConfig);

		JedisClientConfiguration clientConfiguration = builder.build();

		return new JedisConnectionFactory(getStandaloneConfig(), clientConfiguration);
	}

	/**
	 * @see RedisConnectionConfiguration#getStandaloneConfig()
	 */
	private final RedisStandaloneConfiguration getStandaloneConfig() {
		RedisStandaloneConfiguration config = new RedisStandaloneConfiguration();
		config.setHostName(HOST);
		config.setPort(PORT);
		// config.setPassword(RedisPassword.of(this.properties.getPassword()));
		config.setDatabase(0);
		return config;
	}

	public static RedisClientFactory getInstance() {
		return instance;
	}

	public StringRedisTemplate stringRedisTemplate() {
		return getInstance()._stringRedisTemplate;
	}

	public RedisTemplate<Object, Object> redisTemplate() {
		return getInstance()._redisTemplate;
	}
}
