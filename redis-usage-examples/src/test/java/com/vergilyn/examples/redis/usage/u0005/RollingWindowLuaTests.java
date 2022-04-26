package com.vergilyn.examples.redis.usage.u0005;

import com.google.common.collect.Lists;
import com.vergilyn.examples.commons.utils.LuaScriptReadUtils;
import com.vergilyn.examples.redis.usage.AbstractRedisClientTest;
import lombok.SneakyThrows;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.dao.DataAccessException;
import org.springframework.data.redis.connection.RedisConnection;
import org.springframework.data.redis.core.RedisCallback;
import org.springframework.data.redis.core.script.RedisScript;

import java.time.LocalDateTime;
import java.util.List;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

public class RollingWindowLuaTests extends AbstractRedisClientTest {

	private final String script = LuaScriptReadUtils.getScript(this.getClass(), "redis-rolling-window.lua");

	private static AtomicInteger index = new AtomicInteger(0);

	@SneakyThrows
	@ParameterizedTest
	@ValueSource(longs = {1000, 2000, 2000, 2000, 2000, 2000, 2000, 2000, 2000, 2000, 2000, 2000, 2000, 2000, 2000})
	public void test(long millis){
		String key = "rolling-window";
		LocalDateTime now = LocalDateTime.now();
		int i = index.getAndIncrement();

		// 由于redis-lua拒绝随机写，所以可以在代码中调用`redis.call("TIME")`。
		// 并且为了避免各个服务器的时间差，所以可以统一用 redis 的时间（如果是 redis集群？？）。
		// `redis> TIME` 一个包含两个字符串的列表： 第一个字符串是当前时间(以 UNIX 时间戳格式表示)，而第二个字符串是当前这一秒钟已经逝去的微秒数。
		Long redisCurrMillis = _redisTemplate.execute(new RedisCallback<Long>() {
			@Override
			public Long doInRedis(RedisConnection connection) throws DataAccessException {
				// spring-data-redis 在 `2.5+`才支持返回 microseconds： https://github.com/spring-projects/spring-data-redis/pull/526
				// connection.serverCommands().time(TimeUnit)
				return connection.serverCommands().time();
			}
		});

		System.out.printf("[%d]redisCurrMillis: %s ", i, redisCurrMillis);

		List<Object> argv = Lists.newArrayList(redisCurrMillis, 5, 2, 6);

		RedisScript<Boolean> redisScript = RedisScript.of(script, Boolean.class);

		Boolean isLimit = _redisTemplate.execute(redisScript, Lists.newArrayList(key), argv.toArray());
		System.out.printf("[%s]isLimit: %b\n", now.toString(), isLimit);

		TimeUnit.MILLISECONDS.sleep(millis);
	}
}
