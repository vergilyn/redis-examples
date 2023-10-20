package com.vergilyn.examples.redis.usage.u0005;

import com.google.common.util.concurrent.RateLimiter;
import org.junit.jupiter.api.Test;

/**
 *
 * @see com.google.common.util.concurrent.RateLimiter
 * @see com.google.common.util.concurrent.SmoothRateLimiter
 * @see com.google.common.util.concurrent.SmoothRateLimiter.SmoothBursty
 * @see com.google.common.util.concurrent.SmoothRateLimiter.SmoothWarmingUp
 */
public class GuavaRateLimiterTests {

	@Test
	public void test(){
		RateLimiter rateLimiter = RateLimiter.create(2);
		rateLimiter.tryAcquire();

		System.out.println(System.currentTimeMillis());
		System.out.println(System.nanoTime());
	}
}
