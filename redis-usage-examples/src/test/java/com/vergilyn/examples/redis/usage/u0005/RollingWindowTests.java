package com.vergilyn.examples.redis.usage.u0005;

import com.google.common.collect.Sets;
import com.vergilyn.examples.commons.redis.RedisClientFactory;
import com.vergilyn.examples.redis.usage.AbstractRedisClientTest;
import org.apache.commons.collections4.CollectionUtils;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;
import org.springframework.data.redis.core.BoundZSetOperations;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.data.redis.core.ZSetOperations;

import java.time.LocalTime;
import java.time.format.DateTimeFormatter;
import java.util.Set;
import java.util.concurrent.TimeUnit;

@TestInstance(TestInstance.Lifecycle.PER_CLASS)
public class RollingWindowTests extends AbstractRedisClientTest {
	private static final int WINDOW_EXPIRED_MINUTES = 10;
	private static final int LIMIT_MEMBER = 2;
	private static final int LIMIT_TOTAL = 5;

	private RedisTemplate<String, Long> redisTemplate;

	@BeforeAll
	public void beforeAll(){
		redisTemplate = RedisClientFactory.getInstance().redisTemplate();
	}

	/**
	 * 每分钟允许次数 {@linkplain #LIMIT_MEMBER} <br/>
	 * 滑动窗口 {@linkplain #WINDOW_EXPIRED_MINUTES} 内最多允许次数 {@linkplain #LIMIT_TOTAL} <br/>
	 *
	 * @see #buildMember(LocalTime)
	 */
	@Test
	public void test(){
		String key = "test:rolling_window";
		LocalTime n1 = LocalTime.of(12, 0, 1);
		write(key, n1, 1);

		LocalTime n2 = LocalTime.of(12, 0, 2);

		boolean isAllow = isAllow(key, n2);
		System.out.printf("n2: %s, isAllow: %s \n", n2, isAllow);
		if (isAllow){
			write(key, n2, 1);
		}
	}

	/**
	 * 因为不是LUA脚本，无法保证原子性。<br/>
	 * pipeline，不能保存原子性，即中间可以穿插别的命令 <br/>
	 * multi/exec，能保证原子性， 中间不会穿插别的命令，但无法在中途获取命令的返回结果。  <br/>
	 * （模糊记得，虽然 客户端也是一条一条的提交到 redis-server，但redis-server会用一个 集合/数组 保存这组集合，最后exec时 一起执行）
	 *
	 * <br/>
	 * 是否需要保证原子性看实际业务场景，其实如果要精确控制，还要考虑分布式部署后的并发问题，
	 *  还可能要考虑`#isAllow()`和`#write`存在时间差
	 *  （比如控制发送短信频率，某个mobile通过了滑动窗口判断，但是调用`#write`是在真实调用第三方短信接口时，这中间存在一段时间差） <br/>
	 */
	private boolean isAllow(String key, LocalTime now){

		BoundZSetOperations<String, Long> boundZSetOps = redisTemplate.boundZSetOps(key);

		Set<ZSetOperations.TypedTuple<Long>> members = boundZSetOps.rangeWithScores(0, -1);
		if (CollectionUtils.isEmpty(members)){
			// TODO 2022-04-25 添加
			return true;
		}

		LocalTime invalid = now.minusMinutes(WINDOW_EXPIRED_MINUTES);
		Long invalidMember = buildMember(invalid);
		Long nowMember = buildMember(now);

		Set<Long> removes = Sets.newHashSetWithExpectedSize(members.size());

		int total = 0;

		Long timestamp;
		Double score;
		boolean isAllow = true;
		for (ZSetOperations.TypedTuple<Long> member : members) {
			timestamp = member.getValue();
			score = member.getScore();

			if (timestamp <= invalidMember){
				removes.add(timestamp);
				continue;
			}

			// member限制。 不要直接 return，还要维护`invalid`数据
			if (timestamp.equals(nowMember) && score >= LIMIT_MEMBER){
				isAllow = false;
				continue;
			}

			total += score;
		}

		if (CollectionUtils.isNotEmpty(removes)){
			boundZSetOps.remove(removes.toArray());
		}

		return isAllow & total < LIMIT_TOTAL;
	}

	private void write(String key, LocalTime dateTime, int count){
		BoundZSetOperations<Object, Object> boundZSetOps = _redisTemplate.boundZSetOps(key);
		boundZSetOps.incrementScore(buildMember(dateTime), count);
		boundZSetOps.expire(WINDOW_EXPIRED_MINUTES, TimeUnit.MINUTES);
	}

	private Long buildMember(LocalTime dateTime){
		return Long.parseLong(dateTime.format(DateTimeFormatter.ofPattern("HHmm")));
	}
}
