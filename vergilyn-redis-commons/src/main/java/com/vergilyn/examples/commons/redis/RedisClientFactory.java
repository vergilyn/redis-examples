package com.vergilyn.examples.commons.redis;

import com.alibaba.fastjson.parser.Feature;
import com.alibaba.fastjson.serializer.SerializerFeature;
import com.alibaba.fastjson.support.config.FastJsonConfig;
import com.alibaba.fastjson.support.spring.FastJsonRedisSerializer;

import org.springframework.data.redis.connection.RedisConnectionFactory;
import org.springframework.data.redis.connection.RedisStandaloneConfiguration;
import org.springframework.data.redis.connection.jedis.JedisClientConfiguration;
import org.springframework.data.redis.connection.jedis.JedisConnectionFactory;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.serializer.Jackson2JsonRedisSerializer;
import org.springframework.data.redis.serializer.RedisSerializer;

@SuppressWarnings("ALL")
public class RedisClientFactory extends AbstractRedisClient{
	private static RedisClientFactory instance;

	private final RedisTemplate<Object, Object> _redisTemplate;
	private final StringRedisTemplate _stringRedisTemplate;

	private final RedisSerializer defalutRedisSerializer = getFastJsonRedisSerializer();

	private final String host;
	private final int port;

	/**
	 * @see org.springframework.boot.autoconfigure.data.redis.RedisAutoConfiguration
	 */
	private RedisClientFactory(String host, int port) {
		this.host = host;
		this.port = port;

		RedisConnectionFactory redisConnectionFactory = buildRedisConnectionFactory();

		this._redisTemplate = buildRedisTemplate(redisConnectionFactory);
		this._redisTemplate.afterPropertiesSet();

		this._stringRedisTemplate = buildStringRedisTemplate(redisConnectionFactory);
		this._stringRedisTemplate.afterPropertiesSet();
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

	private RedisTemplate<Object, Object> buildRedisTemplate(RedisConnectionFactory connectionFactory){
		RedisTemplate<Object, Object> redisTemplate = new RedisTemplate<>();
		redisTemplate.setConnectionFactory(connectionFactory);

		redisTemplate.setKeySerializer(RedisSerializer.string());
		redisTemplate.setHashKeySerializer(RedisSerializer.string());

		redisTemplate.setValueSerializer(defalutRedisSerializer);
		redisTemplate.setHashValueSerializer(defalutRedisSerializer);

		return redisTemplate;
	}

	/**
	 * @see StringRedisTemplate
	 */
	private StringRedisTemplate buildStringRedisTemplate(RedisConnectionFactory connectionFactory){
		StringRedisTemplate stringRedisTemplate = new StringRedisTemplate();
		stringRedisTemplate.setConnectionFactory(connectionFactory);

		stringRedisTemplate.setKeySerializer(RedisSerializer.string());
		stringRedisTemplate.setValueSerializer(RedisSerializer.string());
		stringRedisTemplate.setHashKeySerializer(RedisSerializer.string());
		stringRedisTemplate.setHashValueSerializer(RedisSerializer.string());

		return stringRedisTemplate;
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

	private Jackson2JsonRedisSerializer getJackson2JsonRedisSerializer() {
		/*Jackson2JsonRedisSerializer jackson2JsonRedisSerializer = new Jackson2JsonRedisSerializer(Object.class);
		ObjectMapper om = new ObjectMapper();
		om.setVisibility(PropertyAccessor.ALL, Visibility.ANY);
		om.enableDefaultTyping(DefaultTyping.NON_FINAL);
		jackson2JsonRedisSerializer.setObjectMapper(om);
		return jackson2JsonRedisSerializer;*/
		throw new UnsupportedOperationException();
	}

	private FastJsonRedisSerializer getFastJsonRedisSerializer() {
		FastJsonRedisSerializer serializer = new FastJsonRedisSerializer(Object.class);
		FastJsonConfig fastJsonConfig = new FastJsonConfig();
		fastJsonConfig.setSerializerFeatures(new SerializerFeature[]{SerializerFeature.WriteClassName});
		fastJsonConfig.setFeatures(new Feature[]{Feature.SupportAutoType});
		serializer.setFastJsonConfig(fastJsonConfig);
		return serializer;
	}
}
