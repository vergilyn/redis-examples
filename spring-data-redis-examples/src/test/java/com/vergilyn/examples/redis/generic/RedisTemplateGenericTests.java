package com.vergilyn.examples.redis.generic;

import java.time.Duration;

import com.alibaba.fastjson.JSON;
import com.vergilyn.examples.redis.AbstractRedisClientTests;

import lombok.Data;
import lombok.NoArgsConstructor;
import org.junit.jupiter.api.Test;
import org.springframework.data.redis.core.RedisTemplate;

/**
 *
 * @author vergilyn
 * @since 2021-05-26
 */
class RedisTemplateGenericTests extends AbstractRedisClientTests {

	@Test
	public void parent(){
		String key = "generic:template:parent";
		Parent value =  new Parent(1000);

		RedisTemplate<String, Parent> parentRedisTemplate = redisTemplate();
		parentRedisTemplate.opsForValue().set(key, value, Duration.ofSeconds(2L));

		Parent parent = parentRedisTemplate.opsForValue().get(key);
		System.out.printf("get parent: %s \n", JSON.toJSONString(parent));
	}

	@Test
	public void child(){
		String key = "generic:template:child";
		Child value =  new Child(1001);

		RedisTemplate<String, Child> parentRedisTemplate = redisTemplate();
		parentRedisTemplate.opsForValue().set(key, value, Duration.ofSeconds(2L));

		Child parent = parentRedisTemplate.opsForValue().get(key);
		System.out.printf("get parent: %s \n", JSON.toJSONString(parent));
	}

	@Data
	@NoArgsConstructor
	static class Parent {
		private Integer parentId;

		public Parent(Integer parentId) {
			this.parentId = parentId;
		}
	}

	@Data
	@NoArgsConstructor
	static class Child {
		private Integer childId;

		public Child(Integer childId) {
			this.childId = childId;
		}
	}
}
