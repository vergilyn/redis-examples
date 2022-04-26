package com.vergilyn.examples.redis.usage.u0102;

import org.springframework.data.redis.core.RedisTemplate;

import java.util.Date;

/**
 * 按天失效模版
 *
 * @author vergilyn
 * @since 2022-03-03
 */
public class DailyTemplate {

	private RedisTemplate redisTemplate;
	private Date date;

	public DailyTemplate(RedisTemplate redisTemplate, Date date) {
		this.redisTemplate = redisTemplate;
		this.date = date;
	}

	public void execute(Function function){
		String key = function.getRedisKey();

		Boolean isExist = redisTemplate.hasKey(key);
		if (isExist){
			return;
		}

		boolean callback = function.callback();
		if (callback){
			redisTemplate.opsForValue().set(key, Boolean.TRUE.toString());
		}
	}

	public static interface Function {
		String getRedisKey();

		boolean callback();
	}
}
