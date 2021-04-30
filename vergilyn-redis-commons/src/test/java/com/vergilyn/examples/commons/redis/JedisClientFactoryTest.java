package com.vergilyn.examples.commons.redis;

import java.time.LocalTime;

import org.assertj.core.api.Assertions;
import org.junit.jupiter.api.Test;
import redis.clients.jedis.Jedis;

class JedisClientFactoryTest {

	@Test
	public void conn(){
		// jedis线程不安全，不能多个线程访问同一个jedis对象
		Jedis jedis = JedisClientFactory.getInstance().jedis();

		String key = this.getClass().getSimpleName();
		String value = LocalTime.now().toString();
		jedis.set(key, value);

		String actual = jedis.get(key);

		// jedis 需要手动调用close
		jedis.close();

		Assertions.assertThat(actual).isEqualTo(value);

		System.out.printf("key: %s, value: %s, actual: %s", key, value, actual);
	}
}