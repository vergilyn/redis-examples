package com.vergilyn.examples.commons.redis;

import org.springframework.data.redis.connection.RedisConnectionFactory;
import org.springframework.data.redis.connection.RedisStandaloneConfiguration;
import org.springframework.data.redis.connection.jedis.JedisClientConfiguration;
import org.springframework.data.redis.connection.jedis.JedisConnectionFactory;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.data.redis.core.StringRedisTemplate;

@SuppressWarnings("ALL")
public class RedisClientFactory extends AbstractRedisClient{
	private final RedisTemplate<Object, Object> _redisTemplate;
	private final StringRedisTemplate _stringRedisTemplate;

	private static RedisClientFactory instance;

	private final String host;
	private final int port;

	/**
	 * @see org.springframework.boot.autoconfigure.data.redis.RedisAutoConfiguration
	 */
	private RedisClientFactory(String host, int port) {
		this.host = host;
		this.port = port;

		RedisConnectionFactory redisConnectionFactory = buildRedisConnectionFactory();

		this._redisTemplate = new RedisTemplate<>();
		this._redisTemplate.setConnectionFactory(redisConnectionFactory);
		// _redisTemplate.setKeySerializer();
		// _redisTemplate.setStringSerializer();
		this._redisTemplate.afterPropertiesSet();

		this._stringRedisTemplate = new StringRedisTemplate();
		this._stringRedisTemplate.setConnectionFactory(redisConnectionFactory);
		this._stringRedisTemplate.afterPropertiesSet();
	}

	/**
	 * @see org.springframework.boot.autoconfigure.data.redis.JedisConnectionConfiguration#createJedisConnectionFactory(org.springframework.beans.factory.ObjectProvider)
	 */
	private RedisConnectionFactory buildRedisConnectionFactory() {
		JedisClientConfiguration.JedisClientConfigurationBuilder builder = JedisClientConfiguration.builder();
		builder.usePooling().poolConfig(poolConfig());

		JedisClientConfiguration clientConfiguration = builder.build();

		return new JedisConnectionFactory(getStandaloneConfig(), clientConfiguration);
	}

	/**
	 * @see org.springframework.boot.autoconfigure.data.redis.RedisConnectionConfiguration#getStandaloneConfig()
	 */
	private final RedisStandaloneConfiguration getStandaloneConfig() {
		RedisStandaloneConfiguration config = new RedisStandaloneConfiguration();
		config.setHostName(host);
		config.setPort(port);
		// config.setPassword(RedisPassword.of(this.properties.getPassword()));
		config.setDatabase(0);
		return config;
	}

	public static RedisClientFactory getInstance() {
		return getInstance(DEFAULT_HOST, DEFAULT_PORT);
	}

	public static RedisClientFactory getInstance(String host, int port) {
		if (instance != null){
			return instance;
		}

		synchronized (RedisClientFactory.class) {
			if (instance != null) {
				return instance;
			}

			instance = new RedisClientFactory(host, port);
			return instance;
		}
	}

	public StringRedisTemplate stringRedisTemplate() {
		return getInstance()._stringRedisTemplate;
	}

	public RedisTemplate<Object, Object> redisTemplate() {
		return getInstance()._redisTemplate;
	}

	public <K, V> RedisTemplate<K, V> redisTemplate(Class<K> kClass, Class<V> vClass) {
		return (RedisTemplate<K, V>) this._redisTemplate;
	}
}
