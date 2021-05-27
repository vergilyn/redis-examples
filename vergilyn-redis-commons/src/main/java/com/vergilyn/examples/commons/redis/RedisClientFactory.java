package com.vergilyn.examples.commons.redis;

import com.vergilyn.examples.commons.serializer.RedisSerializerFactory;

import org.springframework.data.redis.connection.RedisConnectionFactory;
import org.springframework.data.redis.connection.RedisStandaloneConfiguration;
import org.springframework.data.redis.connection.jedis.JedisClientConfiguration;
import org.springframework.data.redis.connection.jedis.JedisConnectionFactory;
import org.springframework.data.redis.core.RedisKeyValueAdapter;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.listener.RedisMessageListenerContainer;
import org.springframework.data.redis.serializer.RedisSerializer;

@SuppressWarnings("ALL")
public class RedisClientFactory extends AbstractRedisClient{
	private static RedisClientFactory instance;

	private final RedisTemplate<Object, Object> _redisTemplate;
	private final StringRedisTemplate _stringRedisTemplate;
	private final RedisMessageListenerContainer _redisMessageListenerContainer;

	private final RedisSerializer defalutRedisSerializer = RedisSerializerFactory.fastjson();

	private final String host;
	private final int port;

	/**
	 * @see org.springframework.boot.autoconfigure.data.redis.RedisAutoConfiguration
	 */
	private RedisClientFactory(String host, int port) {
		this.host = host;
		this.port = port;

		RedisConnectionFactory redisConnectionFactory = buildRedisConnectionFactory();

		this._redisTemplate = instanceRedisTemplate(redisConnectionFactory);
		this._stringRedisTemplate = instanceStringRedisTemplate(redisConnectionFactory);
		this._redisMessageListenerContainer = instanceListenerContainer(redisConnectionFactory);
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

	public <K, V> RedisTemplate<K, V> redisTemplate() {
		return (RedisTemplate<K, V>) this._redisTemplate;
	}

	public RedisMessageListenerContainer redisListenerContainer() {
		return this._redisMessageListenerContainer;
	}

	/**
	 * spring-data-redis默认禁用事务支持。
	 *
	 * @see <a href="https://docs.spring.io/spring-data/redis/docs/2.5.1/reference/html/#tx.spring">
	 *      `@Transactional Support`</a>
	 */
	public void enableTransaction(){
		_stringRedisTemplate.setEnableTransactionSupport(true);
		_redisTemplate.setEnableTransactionSupport(true);
	}

	private RedisTemplate<Object, Object> instanceRedisTemplate(RedisConnectionFactory connectionFactory){
		RedisTemplate<Object, Object> redisTemplate = new RedisTemplate<>();
		redisTemplate.setConnectionFactory(connectionFactory);

		redisTemplate.setKeySerializer(RedisSerializer.string());
		redisTemplate.setHashKeySerializer(RedisSerializer.string());

		redisTemplate.setValueSerializer(defalutRedisSerializer);
		redisTemplate.setHashValueSerializer(defalutRedisSerializer);

		redisTemplate.afterPropertiesSet();
		return redisTemplate;
	}

	/**
	 * @see StringRedisTemplate
	 */
	private StringRedisTemplate instanceStringRedisTemplate(RedisConnectionFactory connectionFactory){
		StringRedisTemplate template = new StringRedisTemplate();
		template.setConnectionFactory(connectionFactory);

		template.afterPropertiesSet();
		return template;
	}

	/**
	 * @see RedisKeyValueAdapter#initMessageListenerContainer()
	 * @see <a href="https://docs.spring.io/spring-data/data-redis/docs/current/reference/html/#redis:reactive:pubsub:subscribe:containers">
	 *          Message Listener Containers</a>
	 */
	private RedisMessageListenerContainer instanceListenerContainer(RedisConnectionFactory connectionFactory){
		RedisMessageListenerContainer container = new RedisMessageListenerContainer();
		container.setConnectionFactory(connectionFactory);

		container.afterPropertiesSet();
		container.start();
		return container;
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


}
