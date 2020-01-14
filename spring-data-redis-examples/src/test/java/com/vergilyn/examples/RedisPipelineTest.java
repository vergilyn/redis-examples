package com.vergilyn.examples;

import java.time.Duration;
import java.util.List;
import java.util.concurrent.ExecutionException;

import com.google.common.collect.Lists;

import io.lettuce.core.RedisFuture;
import io.lettuce.core.cluster.api.async.RedisClusterAsyncCommands;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.dao.DataAccessException;
import org.springframework.data.redis.connection.RedisConnection;
import org.springframework.data.redis.connection.StringRedisConnection;
import org.springframework.data.redis.connection.lettuce.LettuceConnection;
import org.springframework.data.redis.core.RedisCallback;
import org.testng.annotations.DataProvider;
import org.testng.annotations.Test;
import redis.clients.jedis.Jedis;
import redis.clients.jedis.Pipeline;
import redis.clients.jedis.util.RedisOutputStream;

/**
 * @author VergiLyn
 * @date 2019-06-13
 */
@SpringBootTest(classes = SpringDataRedisApplication.class)
@Slf4j
public class RedisPipelineTest extends AbstractTestng {
    /**
     * `get jedis`  --RESP--> `*2\r\n$3\r\nget\r\n$5\r\njedis\r\n`, len=24;
     * jedis-pipeline的client-output-buffer限制：8192 (这个数字也是有意义的，未去了解)。8192 / 24 ≈ 341 条命令为一个数据包。
     *
     * @see RedisOutputStream#RedisOutputStream(java.io.OutputStream) jedis默认限制output-buffer=8192。
     */
    @Test(dataProvider = "pipelineData", threadPoolSize = 2, invocationCount = 1)
    public void jedisPipeline(boolean flag){
        String key = "jedis";

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


    /**
     * `get jedis`  --RESP--> `*2\r\n$3\r\nget\r\n$5\r\njedis\r\n`, len=24;
     * redis --response RESP--> `$3\r\n342\r\n`, len=9.
     */
    @Test
    public void jedisPipelineGet(){
        String key = "jedis";

        Jedis jedis = jedisPool.getResource();
        Pipeline pipeline = jedis.pipelined();

        int num = 0, limit = 400;
        while (num++ < limit) {
            pipeline.get(key);
        }

        List<Object> list = pipeline.syncAndReturnAll();
        jedis.close();

        log.info("exec: jedis-pipeline-get, key: {}, result: {} \r\n", key, StringUtils.join(list, ","));
    }

    /**
     * 感觉是写法问题，也可能是lettuce的问题。
     * 跟jedis-pipeline表现出来并不同，lettuce还是一个完整的命令RESP为一次TCP请求。
     *
     * 代码参考: https://docs.spring.io/spring-data/redis/docs/2.1.8.RELEASE/reference/html/#pipeline
     */
    @Test
    public void lettucePipelineGet(){
        String key = "lettuce";
        int limit = 400;

        List<Object> list = stringRedisTemplate.executePipelined(new RedisCallback<Object>() {
            @Override
            public Object doInRedis(RedisConnection connection) throws DataAccessException {
                StringRedisConnection stringRedisConn = (StringRedisConnection)connection;

                int num = 0;
                while (num++ < limit) {
                    stringRedisConn.get(key.getBytes());
                }
                return null;
            }
        });

        log.info("exec: get, key: {}, result: {} \r\n", key, StringUtils.join(list, ","));
    }

    /**
     * 参考: https://lettuce.io/core/release/reference/index.html#_pipelining_and_command_flushing
     */
    @Test
    public void lettucePipeline(){
        String key = "lettuce";
        int limit = 400;

        LettuceConnection lettuceConnection = (LettuceConnection) stringRedisTemplate.getConnectionFactory().getConnection();
        RedisClusterAsyncCommands<byte[], byte[]> commands = lettuceConnection.getNativeConnection();

        // 如果想达到打包发送请求的效果（类似jedis-匹配李恩），需要设置`autoFlushCommands=false`
        // disable auto-flushing
        commands.setAutoFlushCommands(false);
        commands.setTimeout(Duration.ofMinutes(10));

        // perform a series of independent calls
        List<RedisFuture<byte[]>> futures = Lists.newArrayList();
        for (int i = 0; i < limit; i++) {
            futures.add(commands.get(key.getBytes()));
        }

        // 因为`autoFlushCommands=false`，所以需要手动提交命令
        // write all commands to the transport layer
        commands.flushCommands();

        List<Object> result = Lists.newArrayList();
        futures.forEach(e -> {
            try {
                result.add(new String(e.get()));
            } catch (InterruptedException | ExecutionException ex) {
                ex.printStackTrace();
            }
        });

        // later
        lettuceConnection.close();

        log.info("exec: lettuce-get, key: {}, result: {} \r\n", key, StringUtils.join(result, ","));

    }

    @DataProvider(name = "pipelineData", parallel = true)
    private Object[][] pipelineData(){
        return new Object[][]{{true}, {false}};
    }

}
