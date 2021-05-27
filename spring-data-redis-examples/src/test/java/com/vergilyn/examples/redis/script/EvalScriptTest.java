package com.vergilyn.examples.redis.script;

import com.vergilyn.examples.redis.AbstractRedisClientTests;

import org.junit.jupiter.api.Test;
import org.springframework.data.redis.core.script.RedisScript;
import org.testng.collections.Lists;

/**
 * @author VergiLyn
 * @date 2019-06-24
 */
class EvalScriptTest extends AbstractRedisClientTests {

    /**
     * <a href="https://docs.spring.io/spring-data/redis/docs/2.1.8.RELEASE/reference/html/#scripting">scripting</a>
     */
    @Test
    public void script(){
        String script = "local rs = redis.call('incr', KEYS[1]); redis.call('expire', KEYS[1], ARGV[1]); return rs;";
        String key = "redis:script";
        RedisScript<Long> redisScript = RedisScript.of(script, Long.class);

        Long eval = stringRedisTemplate.execute(redisScript, Lists.newArrayList(key), "10");
        System.out.println("script response >>>> " + eval);
    }
}
