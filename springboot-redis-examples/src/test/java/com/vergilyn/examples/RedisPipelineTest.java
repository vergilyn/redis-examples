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
import redis.clients.util.RedisOutputStream;

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
    public void lettucePipeline(boolean flag){
        String key = "lettuce";
        int limit = 5;

        if (flag){
            List<Object> list = stringRedisTemplate.executePipelined((RedisCallback<Object>) connection -> {
                int num = 0;
                while (num++ < limit) {
                    connection.incr(key.getBytes());
                }
                return null;
            });

            log.info("exec: incr, key: {}, result: {} \r\n", key, StringUtils.join(list, ","));
        }else {

            List<Object> list = stringRedisTemplate.executePipelined((RedisCallback<Object>) connection -> {
                int num = 0;
                while (num++ < limit) {
                    connection.get(key.getBytes());
                }
                return null;
            });

            log.info("exec: get, key: {}, result: {} \r\n", key, StringUtils.join(list, ","));
        }
    }

    /**
     * 针对`get jedis` 大概 341 条命令为一组相同结果。
     * 其单条RESP：`*2\r\n$3\r\nget\r\n$5\r\njedis\r\n` len=24
     * jedis-pipeline的client-buffer限制：8192。8192 / 24 ≈ 341
     *
     * @see RedisOutputStream#RedisOutputStream(java.io.OutputStream) jedis默认限制output-buffer=8192。
     */
    @Test(dataProvider = "pipelineData", threadPoolSize = 2, invocationCount = 1)
    public void jedisPipeline(boolean flag){
        String key = "jedis";
        //  省略其余源码
        Jedis jedis = jedisPool.getResource();
        Pipeline pipeline = jedis.pipelined();
        int num = 0, limit = 342;
        List<Object> list;

        if (flag){
            while (num++ < limit) {
                pipeline.incr(key);
            }

            list = pipeline.syncAndReturnAll();

            log.info("exec: incr, key: {}, result: {} \r\n", key, StringUtils.join(list, ","));
        }else {
            while (num++ < limit) {
                pipeline.get(key);
            }

            list = pipeline.syncAndReturnAll();
            log.info("exec: get, key: {}, result: {} \r\n", key, StringUtils.join(list, ","));
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
