package com.vergilyn.examples;

import java.util.List;

import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.data.redis.core.RedisCallback;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.test.context.testng.AbstractTestNGSpringContextTests;
import org.testng.annotations.DataProvider;
import org.testng.annotations.Test;
import redis.clients.jedis.Jedis;
import redis.clients.jedis.JedisPool;
import redis.clients.jedis.Pipeline;

/**
 * @author VergiLyn
 * @date 2019-06-13
 */
@SpringBootTest(classes = SpringDataRedisApplication.class)
@Slf4j
public class RedisPipelineTest extends AbstractTestNGSpringContextTests {
    @Autowired
    private StringRedisTemplate stringRedisTemplate;
    @Autowired
    private JedisPool jedisPool;

    @Test(dataProvider = "pipelineData", threadPoolSize = 2, invocationCount = 1)
    public void redisPipeline(boolean flag){
        String key = "redis";
        if (flag){
            List<Object> list = stringRedisTemplate.executePipelined((RedisCallback<Object>) connection -> {
                int num = 0;
                while (num++ < 5) {
                    connection.incr(key.getBytes());
                }
                return null;
            });

            log.info("redis >>>> exec: incr, key: {}, result: {} \r\n", key, StringUtils.join(list, ","));
        }else {

            List<Object> list = stringRedisTemplate.executePipelined((RedisCallback<Object>) connection -> {
                int num = 0;
                while (num++ < 5) {
                    connection.get(key.getBytes());
                }
                return null;
            });

            log.info("redis >>>> exec: get, key: {}, result: {} \r\n", key, StringUtils.join(list, ","));
        }
    }

    @Test(dataProvider = "pipelineData", threadPoolSize = 2, invocationCount = 1)
    public void jedisPipeline(boolean flag){
        String key = "jedis";

        Jedis jedis = jedisPool.getResource();
        Pipeline pipeline = jedis.pipelined();
        int num = 0;
        List<Object> list;

        if (flag){
            while (num++ < 5) {
                pipeline.incr(key);
            }
            list = pipeline.syncAndReturnAll();

            log.info("jedis >>>> exec: incr, key: {}, result: {} \r\n", key, StringUtils.join(list, ","));
        }else {
            while (num++ < 5) {
                pipeline.get(key);
            }

            list = pipeline.syncAndReturnAll();

            log.info("jedis >>>> exec: get, key: {}, result: {} \r\n", key, StringUtils.join(list, ","));
        }

        jedis.close();
    }

    @DataProvider(name = "pipelineData", parallel = true)
    private Object[][] pipelineData(){
        return new Object[][]{{true}, {false}};
    }

    private void requestPipeline(String key, Long sleep){
        List<Object> list = stringRedisTemplate.executePipelined((RedisCallback<Object>) connection -> {
            int num = 0;
            while (num++ < 5) {
                connection.incr(key.getBytes());

                if (sleep != null && sleep > 0L){
                    try {
                        Thread.sleep(1000);
                    } catch (Exception e) {

                    }
                }
            }
            return null;
        });

        log.info("Thread-ID: {}, key: {}, result: {} \r\n",
                Thread.currentThread().getId(), key, StringUtils.join(list, ","));
    }
}
