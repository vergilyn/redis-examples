package com.vergilyn.examples.data.redisson.exception;

import com.vergilyn.examples.data.redisson.AbstractRedissonDataApplicationTests;
import lombok.SneakyThrows;
import org.junit.jupiter.api.Test;
import org.redisson.api.RedissonClient;
import org.redisson.spring.data.connection.RedissonConnectionFactory;
import org.redisson.spring.starter.RedissonAutoConfiguration;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.redis.connection.RedisConnectionFactory;

import java.util.Timer;
import java.util.TimerTask;
import java.util.concurrent.TimeUnit;

public class RedissonShutdownExceptionTests extends AbstractRedissonDataApplicationTests {

	@Autowired
	private RedissonClient redissonClient;

	/**
	 * 项目同时依赖 `spring-data-redis` 和 `spring-data-redisson`。
	 * 由 {@link RedissonAutoConfiguration} 可知，
	 * <p> 1. {@link RedisConnectionFactory} 实际是 {@link RedissonConnectionFactory}
	 * <p> 2. 执行redis请求使用的连接是 {@link RedissonAutoConfiguration#redisson()} >> {@link RedissonClient}
	 *
	 * <p><h3>期望复现的异常</h3>
	 * <pre> redisson-spring-data:3.16.0
	 * org.springframework.dao.InvalidDataAccessApiUsageException: Redisson is shutdown; nested exception is org.redisson.RedissonShutdownException: Redisson is shutdown
	 *         at org.redisson.spring.data.connection.RedissonExceptionConverter.convert(RedissonExceptionConverter.java:52)
	 *         at org.redisson.spring.data.connection.RedissonExceptionConverter.convert(RedissonExceptionConverter.java:35)
	 *         at org.springframework.data.redis.PassThroughExceptionTranslationStrategy.translate(PassThroughExceptionTranslationStrategy.java:44)
	 *         at org.redisson.spring.data.connection.RedissonConnection.transform(RedissonConnection.java:195)
	 *         at org.redisson.spring.data.connection.RedissonConnection.syncFuture(RedissonConnection.java:190)
	 *         at org.redisson.spring.data.connection.RedissonConnection.sync(RedissonConnection.java:356)
	 *         at org.redisson.spring.data.connection.RedissonConnection.read(RedissonConnection.java:737)
	 *         at org.redisson.spring.data.connection.RedissonConnection.get(RedissonConnection.java:471)
	 *         at org.springframework.data.redis.core.DefaultValueOperations$1.inRedis(DefaultValueOperations.java:57)
	 *         at org.springframework.data.redis.core.AbstractOperations$ValueDeserializingRedisCallback.doInRedis(AbstractOperations.java:60)
	 *         at org.springframework.data.redis.core.RedisTemplate.execute(RedisTemplate.java:228)
	 *         at org.springframework.data.redis.core.RedisTemplate.execute(RedisTemplate.java:188)
	 *         at org.springframework.data.redis.core.AbstractOperations.execute(AbstractOperations.java:96)
	 *         at org.springframework.data.redis.core.DefaultValueOperations.get(DefaultValueOperations.java:53)
	 *         at com.vergilyn.examples.data.redisson.exception.RedissonShutdownExceptionTests.testMethod(RedissonShutdownExceptionTests.java:63)
	 * </pre>
	 */
	@SneakyThrows
	@Test
	public void testMethod(){
		long delayMs = 500;

		Timer timer = new Timer();
		timer.schedule(new TimerTask() {
			@Override
			public void run() {
				redissonClient.shutdown();
			}
		}, delayMs);

		TimeUnit.MILLISECONDS.sleep(delayMs * 2);

		_stringRedisTemplate.opsForValue().get("test:RedissonShutdownException");

		TimeUnit.SECONDS.sleep(2);
	}
}
