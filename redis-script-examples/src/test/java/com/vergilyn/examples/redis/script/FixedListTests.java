package com.vergilyn.examples.redis.script;

import java.util.Arrays;
import java.util.List;

import com.vergilyn.examples.redis.AbstractJedisClientTest;
import com.vergilyn.examples.redis.utils.LuaScriptReadUtils;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;
import org.springframework.data.redis.core.script.RedisScript;
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
		String lua = LuaScriptReadUtils.getScript("/lua/redis-fixed-list.lua");
		System.out.printf("script >>>> \n%s\n", lua);

		RedisScript<Long> script = RedisScript.of(lua, Long.class);

		List<String> keys = Lists.newArrayList(key);

		List<String> args = Lists.newArrayList();
		args.add(fixedSize + "");
		args.addAll(Arrays.asList(lpushArgs));

		Long llen = _stringRedisTemplate.execute(script, keys, args.toArray());
		assertThat(llen).isEqualTo(5L);

		List<String> lrange = _stringRedisTemplate.boundListOps(key).range(0, -1);
		List<String> expected = Lists.newArrayList("6", "5", "4", "3", "2");
		assertThat(lrange).containsExactlyElementsOf(expected);

		System.out.printf(" >>>> llen: %d, lrange: %s", llen, lrange);
	}
}
