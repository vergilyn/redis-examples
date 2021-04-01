package com.vergilyn.examples.redis.script;

import java.util.List;
import java.util.Set;
import java.util.concurrent.atomic.AtomicLong;

import com.google.common.collect.Lists;
import com.vergilyn.examples.redis.AbstractJedisClientTest;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import redis.clients.jedis.Tuple;

import static org.assertj.core.api.Assertions.assertThat;

/**
 *
 * @author vergilyn
 * @since 2021-04-01
 */
public class RecentlyUsedTests extends AbstractJedisClientTest {
	private final String scriptPath = "/lua/recently-used.lua";
	private final String key = "test-recently-used:409839163";
	private final int fixedSize = 5;
	private final int expiredSecond = 3000;
	private final List<String> _params = Lists.newArrayList();
	private final AtomicLong _score = new AtomicLong(1000);

	@BeforeEach
	public void beforeEach(){
		jedis().del(key);

		_params.add(fixedSize + "");
		_params.add(expiredSecond + "");  // expired (s)
	}

	@Test
	public void valid(){
		String script = getScript(scriptPath);
		System.out.printf("script >>>> \n%s\n", script);

		List<String> keys = Lists.newArrayList(key);

		// ----------------------
		String score1 = getScore();
		List<String> members1 = Lists.newArrayList("1", "2");
		List<String> args1 = args(score1, members1);
		Object result1 = jedis().eval(script, keys, args1);
		System.out.println("eval >>>> result1: " + result1);

		Set<Tuple> zrange1 = zrevrangeByScoreWithScores();
		assertThat(zrange1.size()).isEqualTo(members1.size());
		assertThat(zrange1.stream().map(Tuple::getElement))
				.containsExactlyElementsOf(Lists.newArrayList("2", "1"));

		// ----------------------
		String score2 = getScore();
		List<String> members2 = Lists.newArrayList("3", "4", "5", "6");
		List<String> args2 = args(score2, members2);
		Object result2 = jedis().eval(script, keys, args2);
		System.out.println("eval >>>> result2: " + result2);

		Set<Tuple> zrange2 = zrevrangeByScoreWithScores();
		assertThat(zrange2.size()).isEqualTo(fixedSize);
		assertThat(zrange2.stream().map(Tuple::getElement))
				.containsExactlyElementsOf(Lists.newArrayList("6", "5", "4", "3", "2"));


		// ----假设所有score相同时，`ZREMRANGEBYRANK key 0 1`只会移除2个元素，而不是全部。
		String score3 = score2;
		List<String> members3 = Lists.newArrayList("7", "8");
		List<String> args3 = args(score3, members3);
		Object result3 = jedis().eval(script, keys, args3);
		System.out.println("eval >>>> result3: " + result3);

		Set<Tuple> zrange3 = zrevrangeByScoreWithScores();
		assertThat(zrange3.size()).isEqualTo(fixedSize);
		assertThat(zrange3.stream().map(Tuple::getElement))
				.containsExactlyElementsOf(Lists.newArrayList("8", "7", "6", "5", "4"));

		// ---- 新增"9"， 更新"4"的 score
		String score4 = getScore();
		List<String> members4 = Lists.newArrayList("9", "4");
		List<String> args4 = args(score4, members4);
		Object result4 = jedis().eval(script, keys, args4);
		System.out.println("eval >>>> result4: " + result4);

		Set<Tuple> zrange4 = zrevrangeByScoreWithScores();
		assertThat(zrange4.size()).isEqualTo(fixedSize);
		assertThat(zrange4.stream().map(Tuple::getElement))
				.containsExactlyElementsOf(Lists.newArrayList("9", "4", "8", "7", "6"));
	}

	private List<String> args(String score, List<String> members){
		List<String> args = Lists.newArrayListWithCapacity(_params.size() + 1 + members.size());
		args.add(score);
		args.addAll(members);
		args.addAll(0, _params);
		return args;
	}

	private Set<Tuple> zrevrangeByScoreWithScores(){
		return jedis().zrevrangeByScoreWithScores(key, "+inf", "-inf");
	}

	private String getScore(){
		return _score.incrementAndGet() + "";
	}
}
