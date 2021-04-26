package com.vergilyn.examples.redis.usage.u0001;

import java.util.List;

import com.google.common.collect.Lists;
import com.vergilyn.examples.commons.utils.LuaScriptReadUtils;

import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.RedisScript;

/**
 *
 * @author vergilyn
 * @since 2021-04-26
 */
public class FixedListOperation {
	protected static final String REDIS_FIXED_LIST_LUA;
	protected static final RedisScript<Long> REDIS_FIXED_LIST_SCRIPT;

	static {
		REDIS_FIXED_LIST_LUA = LuaScriptReadUtils.getScript(FixedListOperation.class, "redis-fixed-list.lua");
		REDIS_FIXED_LIST_SCRIPT = RedisScript.of(REDIS_FIXED_LIST_LUA, Long.class);
	}

	/**
	 *
	 * @param redisTemplate
	 * @param key
	 * @param fixedSize
	 * @param lpushArgs
	 * @return size of the list after lpush
	 */
	public static Long execute(StringRedisTemplate redisTemplate, String key, int fixedSize, List<String> lpushArgs){
		List<String> keys = Lists.newArrayList(key);

		List<String> args = Lists.newArrayList();
		args.add(fixedSize + "");
		args.addAll(lpushArgs);

		return redisTemplate.execute(REDIS_FIXED_LIST_SCRIPT, keys, args.toArray());
	}
}
