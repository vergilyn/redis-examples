package com.vergilyn.examples.redis.usage.u0002;

import java.util.List;
import java.util.Set;
import java.util.concurrent.atomic.AtomicLong;

import com.google.common.collect.Lists;
import com.vergilyn.examples.redis.usage.AbstractRedisClientTest;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.data.redis.core.ZSetOperations.TypedTuple;

import static org.assertj.core.api.Assertions.assertThat;

/**
 *
 * @author vergilyn
 * @since 2021-04-01
 */
public class RecentlyUsedTests extends AbstractRedisClientTest {
	private final String key = "test-recently-used:409839163";
	private final int fixedSize = 5;
	private final int expiredSecond = 3000;
	private final List<String> _params = Lists.newArrayList();
	private final AtomicLong _score = new AtomicLong(1000);

	@BeforeEach
	public void beforeEach(){
		_stringRedisTemplate.delete(key);

		_params.add(fixedSize + "");
		_params.add(expiredSecond + "");  // expired (s)
	}

	/**
	 * vergilyn-question, 2021-04-02 >>>>
	 *   一般redis sort-set中只保存关键的ID，然后再通过ID查询完整数据。
	 *   <pre>
	 *   Q1. 分页情况下，如果redis返回的某页数据中的 某条数据被删除了，要如何处理？
	 *     1) 直接不返回，那么会导致 当前页的数据不全，比如每页15条，只显示了13条。（补全的话实现起来貌似蛮麻烦的）
	 *     2) 填充一个占位无效数据，相对`1)`简单很多，但是对使用者不友好。（万一整页都是无效数据！）
	 *
	 *   期望效果：每页数据完整，且不存在无效数据。（如何实现？）
	 *   </pre>
	 */
	@Test
	public void valid(){
		List<String> keys = Lists.newArrayList(key);

		// ----------------------
		String score1 = getScore();
		List<String> members1 = Lists.newArrayList("1", "2");
		List<String> args1 = args(score1, members1);
		Boolean result1 = RecentlyUsedOperation.execute(_stringRedisTemplate, keys, args1);
		System.out.println("eval >>>> result1: " + result1);

		Set<TypedTuple<String>> zrange1 = zrevrangeByScoreWithScores();
		assertThat(zrange1.size()).isEqualTo(members1.size());
		assertThat(zrange1.stream().map(TypedTuple::getValue))
				.containsExactlyElementsOf(Lists.newArrayList("2", "1"));

		// ----------------------
		String score2 = getScore();
		List<String> members2 = Lists.newArrayList("3", "4", "5", "6");
		List<String> args2 = args(score2, members2);
		Boolean result2 = RecentlyUsedOperation.execute(_stringRedisTemplate, keys, args2);
		System.out.println("eval >>>> result2: " + result2);

		Set<TypedTuple<String>> zrange2 = zrevrangeByScoreWithScores();
		assertThat(zrange2.size()).isEqualTo(fixedSize);
		assertThat(zrange2.stream().map(TypedTuple::getValue))
				.containsExactlyElementsOf(Lists.newArrayList("6", "5", "4", "3", "2"));


		// ----假设所有score相同时，`ZREMRANGEBYRANK key 0 1`只会移除2个元素，而不是全部。
		String score3 = score2;
		List<String> members3 = Lists.newArrayList("7", "8");
		List<String> args3 = args(score3, members3);
		Boolean result3 = RecentlyUsedOperation.execute(_stringRedisTemplate, keys, args3);
		System.out.println("eval >>>> result3: " + result3);

		Set<TypedTuple<String>> zrange3 = zrevrangeByScoreWithScores();
		assertThat(zrange3.size()).isEqualTo(fixedSize);
		assertThat(zrange3.stream().map(TypedTuple::getValue))
				.containsExactlyElementsOf(Lists.newArrayList("8", "7", "6", "5", "4"));

		// ---- 新增"9"， 更新"4"的 score
		String score4 = getScore();
		List<String> members4 = Lists.newArrayList("9", "4");
		List<String> args4 = args(score4, members4);
		Boolean result4 = RecentlyUsedOperation.execute(_stringRedisTemplate, keys, args4);
		System.out.println("eval >>>> result4: " + result4);

		Set<TypedTuple<String>> zrange4 = zrevrangeByScoreWithScores();
		assertThat(zrange4.size()).isEqualTo(fixedSize);
		assertThat(zrange4.stream().map(TypedTuple::getValue))
				.containsExactlyElementsOf(Lists.newArrayList("9", "4", "8", "7", "6"));
	}

	private List<String> args(String score, List<String> members){
		List<String> args = Lists.newArrayListWithCapacity(_params.size() + 1 + members.size());
		args.add(score);
		args.addAll(members);
		args.addAll(0, _params);
		return args;
	}

	private Set<TypedTuple<String>> zrevrangeByScoreWithScores(){
		return _stringRedisTemplate.boundZSetOps(key).reverseRangeByScoreWithScores(Double.MIN_VALUE, Double.MAX_VALUE);
	}

	private String getScore(){
		return _score.incrementAndGet() + "";
	}
}
