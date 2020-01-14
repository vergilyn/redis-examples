package com.vergilyn.examples;

import org.springframework.data.redis.core.script.DefaultRedisScript;
import org.testng.annotations.Test;
import org.testng.collections.Lists;
import redis.clients.jedis.Jedis;

/**
 * @author VergiLyn
 * @date 2019-06-24
 */
public class EvalScriptTest extends AbstractTestng {
    private static final String script = "return redis.call('incr', 'incr')";

    @Test
    public void jedis(){
        Jedis jedis = jedisPool.getResource();

        // Long eval = (Long) jedis.eval(script); // java.lang.String cannot be cast to java.lang.Long
        Long eval = (Long) jedis.eval(script, 0);  // params 可以不传，但不能传null
        System.out.println(eval);
    }

    /**
     * <a href="https://docs.spring.io/spring-data/redis/docs/2.1.8.RELEASE/reference/html/#scripting">scripting</a>
     */
    @Test
    public void redisTemplate(){
        DefaultRedisScript<Long> redisScript = new DefaultRedisScript<>(script, Long.class);

        Long eval = stringRedisTemplate.execute(redisScript, Lists.newArrayList(), "");

        System.out.println(eval);
    }
}
