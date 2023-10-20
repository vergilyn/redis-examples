package com.vergilyn.examples.redis.usage.u0103;

import com.alibaba.fastjson.JSON;
import com.vergilyn.examples.redis.usage.AbstractRedisClientTest;
import lombok.Data;
import org.junit.jupiter.api.Test;
import org.springframework.dao.DataAccessException;
import org.springframework.data.redis.connection.RedisConnection;
import org.springframework.data.redis.core.RedisCallback;

import java.io.Serializable;
import java.util.List;

/**
 *
 * @author vergilyn
 * @since 2023-02-14
 *
 * @see <a href="https://github.com/brandur/redis-cell">Github, redis-cell</a>
 */
public class RedisCellTests extends AbstractRedisClientTest {
	private static final String REDIS_CELL_COMMAND = "CL.THROTTLE";


	@Test
	public void test(){
		String key = "test:redis-cell";

		RedisCellResp resp = callRedisCell(key, "15", "30", "60", "1");

		System.out.println(resp);
	}

	/**
	 * <pre>
	 *   CL.THROTTLE <key> <max_burst> <count per period> <period> [<quantity>]
	 *
	 *   $> CL.THROTTLE rediscell 15 30 60 1
	 *
	 * </pre>
	 */
	protected RedisCellResp callRedisCell(String key, String maxBurst, String countPerPeriod, String period, String quantity){

		return _redisTemplate.execute(new RedisCallback<RedisCellResp>() {
			@Override
			public RedisCellResp doInRedis(RedisConnection connection) throws DataAccessException {

				Object value = connection.execute(REDIS_CELL_COMMAND, key.getBytes(), maxBurst.getBytes(),
				                                    countPerPeriod.getBytes(), period.getBytes(), quantity.getBytes());

				List<Long> values = (List<Long>) value;

				RedisCellResp result = new RedisCellResp();
				result.setCode(values.get(0));
				result.setTotalLimit(values.get(1));
				result.setRemaining(values.get(2));
				result.setNextSeconds(values.get(3));
				result.setMaxCapacitySeconds(values.get(4));

				return result;
			}
		});

	}

	@Data
	static class RedisCellResp implements Serializable {
		private static final Long CODE_ALLOWED = 0L;
		private static final Long CODE_LIMITED = 1L;


		/**
		 * Whether the action was limited:
		 * <pre>
		 *     `0` indicates the action is allowed.
		 *     `1` indicates that the action was limited/blocked.
		 * </pre>
		 */
		private Long code;

		/**
		 * The total limit of the key (`max_burst` + 1).
		 * This is equivalent to the common `X-RateLimit-Limit` HTTP header.
		 */
		private Long totalLimit;

		/**
		 * The remaining limit of the key. Equivalent to `X-RateLimit-Remaining`.
		 */
		private Long remaining;

		/**
		 * The number of seconds until the user should retry,
		 * and always `-1` if the action was allowed.
		 * Equivalent to `Retry-After`.
		 */
		private Long nextSeconds;

		/**
		 * The number of seconds until the limit will reset to its maximum capacity.
		 * Equivalent to `X-RateLimit-Reset`.
		 * 表示多久后令牌桶中的令牌会存满
		 */
		private Long maxCapacitySeconds;

		public boolean isAllowed(){
			return CODE_ALLOWED.equals(this.code);
		}

		@Override
		public String toString() {
			return JSON.toJSONString(this);
		}
	}
}
