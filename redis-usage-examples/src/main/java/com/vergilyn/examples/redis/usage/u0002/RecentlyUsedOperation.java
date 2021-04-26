package com.vergilyn.examples.redis.usage.u0002;

import java.util.List;

import com.google.common.collect.Lists;
import com.vergilyn.examples.commons.utils.LuaScriptReadUtils;

import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.RedisScript;

public class RecentlyUsedOperation {
	protected static final String RECENTLY_USED_LUA;
	protected static final RedisScript<Boolean> RECENTLY_USED_SCRIPT;

	static {
		RECENTLY_USED_LUA = LuaScriptReadUtils.getScript(RecentlyUsedOperation.class, "recently-used-write.lua");
		RECENTLY_USED_SCRIPT = RedisScript.of(RECENTLY_USED_LUA, Boolean.class);
	}

	public static Boolean execute(StringRedisTemplate redisTemplate, String key,
			long fixedSize, long expiredSeconds, double score, List<String> members){

		List<String> keys = Lists.newArrayList(key);

		List<String> args = Lists.newArrayListWithCapacity(members.size() + 2);
		args.add(fixedSize + "");
		args.add(expiredSeconds + "");
		args.add(score + "");
		args.addAll(members);

		return execute(redisTemplate, keys, args);
	}

	public static Boolean execute(StringRedisTemplate redisTemplate, List<String> keys, List<String> args){
		return redisTemplate.execute(RECENTLY_USED_SCRIPT, keys, args.toArray());
	}
}
