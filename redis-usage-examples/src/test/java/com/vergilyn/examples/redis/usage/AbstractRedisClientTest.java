package com.vergilyn.examples.redis.usage;

import java.util.concurrent.Semaphore;
import java.util.concurrent.TimeUnit;

import com.vergilyn.examples.commons.redis.RedisClientFactory;

import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.data.redis.core.StringRedisTemplate;

public abstract class AbstractRedisClientTest {
	protected final StringRedisTemplate _stringRedisTemplate = RedisClientFactory.getInstance().stringRedisTemplate();
	protected final RedisTemplate<Object, Object> _redisTemplate = RedisClientFactory.getInstance().redisTemplate();

	/**
	 *
	 * @param timeout "<= 0" prevent exit.
	 * @param unit timeout unit
	 */
	protected void awaitExit(long timeout, TimeUnit unit){
		try {
			final Semaphore semaphore = new Semaphore(0);
			if (timeout > 0){
				semaphore.tryAcquire(timeout, unit);
			}else {
				semaphore.acquire();
			}
		} catch (InterruptedException e) {
		}
	}
}
