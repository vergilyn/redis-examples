package com.vergilyn.examples.redis.usage.u0001;

import java.util.Arrays;
import java.util.List;

import com.vergilyn.examples.redis.usage.AbstractJedisClientTest;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;
import org.testng.collections.Lists;

import static org.assertj.core.api.Assertions.assertThat;

@TestInstance(TestInstance.Lifecycle.PER_CLASS)
public class FixedListTests extends AbstractJedisClientTest {
	private final String key = "test-fixed-list";
	private final int fixedSize = 5;
	private final String[] lpushArgs = {"3", "4", "5", "6"};

	@BeforeEach
	public void beforeEach(){
		_stringRedisTemplate.delete(key);
		_stringRedisTemplate.boundListOps(key).leftPushAll("1", "2");
	}

	@Test
	public void script(){
		Long llen = FixedListOperation.execute(_stringRedisTemplate, key, fixedSize, Arrays.asList(lpushArgs));
		assertThat(llen).isEqualTo(5L);

		List<String> lrange = _stringRedisTemplate.boundListOps(key).range(0, -1);
		List<String> expected = Lists.newArrayList("6", "5", "4", "3", "2");
		assertThat(lrange).containsExactlyElementsOf(expected);

		System.out.printf(" >>>> llen: %d, lrange: %s", llen, lrange);
	}
}
