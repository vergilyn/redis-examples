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
	static final String KEY_VALUE = LocalTime.now().toString();
	@BeforeEach
	public void beforeEach(){
		redisKeyspaceListener.registerRedisKeyExpirationEventMessageListener();

		stringRedisTemplate.boundValueOps("redis:key-expired")
				.set(KEY_VALUE, KEY_EXPIRED, TimeUnit.SECONDS);
	}

	@SneakyThrows
	@Test
	public void listener(){
		registerAndGetBean(RedisKeyExpiredEventListener.class);

		awaitExit(KEY_EXPIRED * 2, TimeUnit.SECONDS);
	}

	private static class RedisKeyExpiredEventListener implements ApplicationListener<RedisKeyExpiredEvent>{

		@Override
		public void onApplicationEvent(RedisKeyExpiredEvent event) {
			System.out.printf("[%s]RedisKeyExpiredEventListener >>>> ", LocalTime.now().toString());

			// value = null!
			System.out.printf("\n\tkeyspace: '%s', id: '%s', actual-value: '%s', expected-value: '%s'\n",
					event.getKeyspace(), new String(event.getId()),
					event.getValue(), KEY_VALUE);
		}
	}
}
