package com.vergilyn.examples.redis.usage.u0005;

import com.google.common.collect.Lists;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.RandomUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.data.redis.core.script.RedisScript;
import org.springframework.stereotype.Component;

import java.util.List;

@Slf4j
@Component
public class RollingWindowRateLimitCacheImpl {

	private static final String LF = "\n";
	private static final String LUA_SCRIPT = buildLuaScript();
	private static final RedisScript<Boolean> REDIS_SCRIPT = RedisScript.of(LUA_SCRIPT, Boolean.class);

	@Autowired
	private RedisTemplate<String, Boolean> redisTemplate;

	/**
	 *
	 * @param key 限流的key
	 * @param currentMillis 请求时间，时间戳（单位：毫秒）。备注：为了防止时间差，可以调用`redis> time`，统一用redis服务器的时间。
	 * @param seconds 限制时间，单位：秒。（也是redis-key的失效时间）
	 * @param times 限制时间{@code seconds}内允许的次数
	 * @param maxZsetEntries 集合最大元素个数。（必须大于{@code times}, 默认  `2 * times`）
	 *
	 * @return true，达到调用限制；false，未触发限制。
	 */
	public boolean isLimitRate(String key, long currentMillis, int seconds, int times, int maxZsetEntries)  {
		List<String> keys = Lists.newArrayList(key);

		maxZsetEntries = maxZsetEntries < times ? 2 * times : maxZsetEntries;

		// zset-max-ziplist-entries 128。
		// maxZsetEntries = Math.max(maxZsetEntries, 128);

		String randomStr = String.format("%03d", RandomUtils.nextInt(0, 1000));
		Object[] argv = new Object[]{currentMillis, seconds, times, maxZsetEntries, randomStr};

		Boolean isLimit = redisTemplate.execute(REDIS_SCRIPT, keys, argv);
		if (isLimit == null){
			throw new RuntimeException("滑动窗口redis-lua返回`null`");
		}

		return isLimit;
	}

	/**
	 * <pre>
	 * 	KEYS[1]:
	 * 	ARGV[1]: 当前时间戳，毫秒。备注：由于redis-lua拒绝随机写，所以可以在代码中调用`redis.call("TIME")`。
	 * 	ARGV[2]：时间，单位秒。（也是key的失效时间）
	 * 	ARGV[3]: 限流次数
	 * 	ARGV[4]: zset集合最大元素数量
	 * 	ARGV[5]: 随机数，避免 zset.member 重复。
	 * </pre>
	 *
	 * <p>
	 * FIXME 2022-04-26 实现方案的缺点
	 *   1) 只能精确到 毫秒。(具体看传入的 `ARGV[1]` 的精度， 相应的要调整 `limitMillis`的计算规则)
	 *   2) 依赖传入的 `currTimestampMillis`，如果服务器之间时间差过大，会导致此滑动窗口限制错误！
	 */
	private static String buildLuaScript(){
		return "local currTimestampMillis = tonumber(ARGV[1]);" + LF
			+ "local limitSeconds = tonumber(ARGV[2]);" + LF
			+ "local limitCount = tonumber(ARGV[3]);" + LF
			+ "local maxZsetEntries = tonumber(ARGV[4]);" + LF
			+ "local randomStr = ARGV[5];" + LF

			// 最早的滑动窗口值
			+ "local minScore = currTimestampMillis - (limitSeconds * 1000);" + LF
            + "local validCount = redis.call(\"ZCOUNT\", KEYS[1], minScore, \"+INF\");" + LF

			// 返回值：true-触发限制，false-未触发限制
			// 如果触发限制 需要返回“最近可以解除限制的时间戳”可以调用 `redis.call("ZRANGEBYSCORE", KEYS[1], minScore, "+INF", "LIMIT", "0", "1")[1]`
			+ "local isLimit = true;" + LF
            + "if (validCount < limitCount) then" + LF
				// 未触发限制，将当前值写入。 避免`zset.member`重复，所以加入`randomStr`。
			+ "  redis.call(\"ZADD\", KEYS[1], currTimestampMillis, currTimestampMillis .. randomStr);" + LF
			+ "  isLimit = false;" + LF
			+ "end" + LF

			// 限制集合大小，清除没用的集合成员
			+ "local total = redis.call(\"ZCARD\", KEYS[1]);" + LF
			+ "if (total > maxZsetEntries) then" + LF
			+ "  redis.call(\"ZREMRANGEBYSCORE\", KEYS[1], \"-INF\", \"(\" .. minScore);" + LF
			+ "end" + LF
			// 设置key的失效时间
			+ "redis.call(\"EXPIRE\", KEYS[1], limitSeconds);" + LF
			+ "return isLimit;";
	}
}
