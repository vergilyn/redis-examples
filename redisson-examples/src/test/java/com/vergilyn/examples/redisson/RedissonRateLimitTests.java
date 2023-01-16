package com.vergilyn.examples.redisson;

import org.apache.commons.lang3.RandomUtils;
import org.junit.jupiter.api.Test;
import org.redisson.RedissonRateLimiter;
import org.redisson.api.RRateLimiter;
import org.redisson.api.RateIntervalUnit;
import org.redisson.api.RateType;
import org.redisson.api.RedissonClient;
import org.redisson.client.protocol.RedisCommand;

import java.time.Duration;
import java.time.LocalDateTime;
import java.time.temporal.ChronoUnit;
import java.util.concurrent.TimeUnit;

/**
 * <h3>备注</h3>
 * 并不喜欢redisson中的 rate-limit 实现，但可以参考思路。
 * （API相对复杂。比如 多个key，区分配置、滑动窗口数据；多个API，分别设置规则、失效时间。）
 *
 * @author dingmaohai
 * @version v1.0
 * @since 2023/01/16 17:24
 */
public class RedissonRateLimitTests {

	/**
	 * FIXME 2023-01-16，redisson无法获取有效的 rate-token时，redis-lua脚本会返回 “xx毫秒，表示xx毫秒后一定会释放1个被占用的令牌”，
	 *   但是在 java-API 中并未体现，根据此返回值，可以比较友好的 `sleep(xx)`
	 *
	 * @see RedissonRateLimiter#tryAcquireAsync(RedisCommand, Long)
	 * @see RedissonRateLimiter#tryAcquireAsync(long, long)
	 */
	@Test
	public void test(){

		RedissonClient redissonClient = RedissonClientFactory.createRedissonClient();

		RRateLimiter rateLimiter = redissonClient.getRateLimiter("vergilyn:20221208");

		// 每3秒，允许2次
		// OVERALL（集群）: Total rate for all RateLimiter instances
		// PER_CLIENT（单机or单实例）: Total rate for all RateLimiter instances working with the same Redisson instance
		rateLimiter.setRate(RateType.OVERALL, 5, 30, RateIntervalUnit.MINUTES);

		// 设置相关 key 的失效时间
		rateLimiter.expire(Duration.of(10, ChronoUnit.MINUTES));

		for (int i = 0; i < 10; i++) {
			boolean b = rateLimiter.tryAcquire(1);

			print(i + "-tryAcquire = " + b);

			sleepMs(RandomUtils.nextLong(1000, 2000));
		}
	}

	private void print(String msg){
		System.out.printf("%s >>>> %s \n", LocalDateTime.now(), msg);
	}

	private void sleepMs(long timeout){
		try {
			TimeUnit.MILLISECONDS.sleep(timeout);
		} catch (InterruptedException ignored) {
		}
	}
}
