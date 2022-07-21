package com.vergilyn.examples.redis.feature;

import com.vergilyn.examples.redis.AbstractRedisClientTests;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.data.redis.RedisAutoConfiguration;
import org.springframework.data.redis.connection.RedisConnectionFactory;
import org.springframework.data.redis.core.RedisTemplate;

import java.io.Serializable;

public class GenericRedisTemplateTests extends AbstractRedisClientTests {

	@Autowired
	private RedisTemplate<String, Person> personRedisTemplate;
	@Autowired
	private RedisTemplate<String, Email> emailRedisTemplate;
	@Autowired
	private RedisTemplate<String, Generic<Number>> genericRedisTemplate;

	/**
	 * 实际上 `personRedisTemplate == emailRedisTemplate`，<b>泛型并没有破坏单例！</b>
	 *
	 * @see RedisAutoConfiguration#redisTemplate(RedisConnectionFactory)
	 * @see RedisAutoConfiguration#stringRedisTemplate(RedisConnectionFactory)
	 */
	@Test
	public void test(){
		System.out.println("personRedisTemplate >>>> " + personRedisTemplate);
		System.out.println("emailRedisTemplate  >>>> " + emailRedisTemplate);

		Assertions.assertEquals(personRedisTemplate, emailRedisTemplate);
	}


	@Data
	@NoArgsConstructor
	public static class Person implements Serializable {
		private String username;
	}

	@Data
	@NoArgsConstructor
	public static class Email implements Serializable {
		private String email;
	}

	@Data
	@NoArgsConstructor
	public static class Generic<T extends Number> implements Serializable {
		private T value;
	}
}
