package com.vergilyn.examples.redis.feature;

import com.alibaba.fastjson.serializer.SerializerFeature;
import com.vergilyn.examples.commons.serializer.RedisSerializerFactory;
import com.vergilyn.examples.redis.AbstractRedisClientTests;
import com.vergilyn.examples.redis.autoconfigred.SliceTestRedisConfiguration;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.redis.connection.RedisConnectionFactory;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.data.redis.serializer.RedisSerializer;
import org.springframework.data.redis.serializer.StringRedisSerializer;

import java.io.Serializable;
import java.time.LocalDateTime;

public class JsonClassInfoTests extends AbstractRedisClientTests {

	@Autowired
	private RedisTemplate<String, PersonInfo> redisTemplate;

	private final PersonInfo _person = new PersonInfo("vergilyn", LocalDateTime.now(), 10086);

	/**
	 *
	 * <p> 相关配置：{@linkplain RedisTemplate#setValueSerializer(RedisSerializer)}
	 * <pre>
	 *  - {@linkplain SliceTestRedisConfiguration#redisTemplate(RedisConnectionFactory, StringRedisSerializer)}
	 *  - {@linkplain RedisSerializerFactory#fastjson()}
	 *  - {@linkplain SerializerFeature#WriteClassName}
	 * </pre>
	 *
	 * @see com.alibaba.fastjson.support.spring.FastJsonRedisSerializer
	 * @see com.alibaba.fastjson.support.spring.GenericFastJsonRedisSerializer
	 */
	@Test
	public void fastJsonRedisSerializer(){
		String key = "class:person:include";

		redisTemplate.opsForValue().set(key, _person);

		String json = stringRedisTemplate.opsForValue().get(key);

		//  {
		// 	   "@type": "com.vergilyn.examples.redis.feature.JsonClassInfoTests$PersonInfo",
		// 	   "code": 10086,
		// 	   "localDateTime": "2022-06-08T09:13:09.676",
		// 	   "name": "vergilyn"
		// 	}
		System.out.println("value >>>> " + json);
	}

	/**
	 * @see org.springframework.data.redis.serializer.Jackson2JsonRedisSerializer
	 * @see org.springframework.data.redis.serializer.GenericJackson2JsonRedisSerializer
	 */
	@Test
	public void jackson2JsonRedisSerializer(){

	}


	@Data
	@NoArgsConstructor
	public static class PersonInfo implements Serializable {
		private String name;

		private LocalDateTime localDateTime;

		private Integer code;

		public PersonInfo(String name, LocalDateTime localDateTime, Integer code) {
			this.name = name;
			this.localDateTime = localDateTime;
			this.code = code;
		}
	}
}
