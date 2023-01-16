package com.vergilyn.examples.redis.usage.u0005;

import com.google.common.collect.Lists;
import com.vergilyn.examples.commons.utils.LuaScriptReadUtils;
import com.vergilyn.examples.redis.usage.AbstractRedisClientTest;
import lombok.SneakyThrows;
import org.apache.commons.lang3.RandomUtils;
import org.junit.jupiter.api.RepeatedTest;
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

public class SlidlingWindowLuaTests extends AbstractRedisClientTest {

	private final String script = LuaScriptReadUtils.getScript(this.getClass(), "redis-sliding-window.lua");

	private static AtomicInteger index = new AtomicInteger(0);

	/**
	 * lua 可以返回 “最近解除限制的时间戳”，用于某些情况下计算 下次允许执行的时间。
	 *
	 * <p>
	 * 特别注意: <br/>
	 *   1. <b>如果服务器 时间差过大，会导致此滑动窗口异常。</b> <br/>
	 *   例如 服务器C 比 服务器A 快1天，那么如果 服务器C先执行，那么 服务器A 永远处于限制状态。<br/>
	 *   反之，如果 服务器A 先执行，服务器C 始终未被限制，且导致 服务器A 永远处于限制状态。 <br/>
	 *
	 *   <b>解决办法：</b>使用同一台服务生成时间戳。
	 *
	 * @see org.redisson.RedissonRateLimiter#tryAcquireAsync(org.redisson.client.protocol.RedisCommand, Long)
	 */
	@SneakyThrows
	@ParameterizedTest
	@ValueSource(longs = {1000, 2000, 2000, 2000, 2000, 2000, 2000, 2000, 2000, 2000, 2000, 2000, 2000, 2000, 2000})
	// @ValueSource(longs = {1000})
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

		// redisCurrMillis = 1651958964458L - 100;
		System.out.printf("[%d]redisCurrMillis: %s ", i, redisCurrMillis);

		// zset-max-ziplist-entries 128   所以没有太多必要比`128`小，除非极致的期望减少redis内存占用。
		//  平衡 空间 和 时间 考虑。
		int maxZsetEntries = Math.max(6, 120);
		// 因为ZSET.member = `{redisCurrMillis}-{randomStr}`。避免重复的 member
		String randomStr = String.format("%03d", RandomUtils.nextInt(0, 1000)) ;
		List<Object> argv = Lists.newArrayList(redisCurrMillis, 5, 2, maxZsetEntries, randomStr);

		RedisScript<Long> redisScript = RedisScript.of(script, Long.class);

		Long lately = _redisTemplate.execute(redisScript, Lists.newArrayList(key), argv.toArray());
		System.out.printf("[%s] isLimit: %s\n", now.toString(), lately);

		TimeUnit.MILLISECONDS.sleep(millis);
	}

	@RepeatedTest(10)
	public void testTTL(){
		String key = "test-ttl";
		_redisTemplate.opsForValue().set(key, "1", 10, TimeUnit.SECONDS);

		// 有概率拿到 10！
		Long expire = _redisTemplate.getExpire(key, TimeUnit.SECONDS);
		System.out.println("expire: " + expire);
	}
}
