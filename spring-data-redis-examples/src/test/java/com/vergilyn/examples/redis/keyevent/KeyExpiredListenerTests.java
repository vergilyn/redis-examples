package com.vergilyn.examples.redis.keyevent;

import java.time.LocalTime;
import java.util.concurrent.TimeUnit;

import com.vergilyn.examples.redis.AbstractRedisClientTests;

import lombok.SneakyThrows;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.context.ApplicationListener;
import org.springframework.data.redis.core.RedisKeyExpiredEvent;

/**
 * @author vergilyn
 * @since 2021-05-27
 */
class KeyExpiredListenerTests extends AbstractRedisClientTests {

	static final long KEY_EXPIRED = TimeUnit.SECONDS.toSeconds(2);
	@BeforeEach
	public void beforeEach(){
		redisKyesapceListener.registerRedisKeyExpirationEventMessageListener();

		stringRedisTemplate.boundValueOps("redis:key-expired")
				.set(LocalTime.now().toString(), KEY_EXPIRED, TimeUnit.SECONDS);
	}

	@SneakyThrows
	@Test
	public void listener(){
		registerAndGetBean(RedisKeyExpiredEventListener.class);

		awaitExit(KEY_EXPIRED * 2, TimeUnit.SECONDS);
	}

	public static class RedisKeyExpiredEventListener implements ApplicationListener<RedisKeyExpiredEvent>{

		@Override
		public void onApplicationEvent(RedisKeyExpiredEvent event) {
			System.out.print("RedisKeyExpiredEventListener >>>> ");

			// value = null!
			System.out.printf("keyspace: %s, id: %s, value: %s\n",
					event.getKeyspace(), new String(event.getId()), event.getValue());
		}
	}
}
