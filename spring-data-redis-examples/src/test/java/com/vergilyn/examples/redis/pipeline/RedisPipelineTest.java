package com.vergilyn.examples.redis.pipeline;

import java.time.Duration;
import java.util.List;
import java.util.concurrent.ExecutionException;

import com.google.common.collect.Lists;
import com.vergilyn.examples.redis.AbstractRedisClientTests;

import io.lettuce.core.RedisFuture;
import io.lettuce.core.cluster.api.async.RedisClusterAsyncCommands;
import org.apache.commons.lang3.StringUtils;
import org.springframework.dao.DataAccessException;
import org.springframework.data.redis.connection.RedisConnection;
import org.springframework.data.redis.connection.StringRedisConnection;
import org.springframework.data.redis.connection.lettuce.LettuceConnection;
import org.springframework.data.redis.core.RedisCallback;
import org.testng.annotations.Test;

/**
 * @author VergiLyn
 * @date 2019-06-13
 */
public class RedisPipelineTest extends AbstractRedisClientTests {

    /**
     * 跟jedis-pipeline表现出来并不同，lettuce还是一个完整的命令RESP为一次TCP请求。
     *
     * @see <a href="https://emacsist.github.io/2019/07/30/spring-data-redis%E4%B8%8Elettuce-%E4%BD%BF%E7%94%A8-pipeline-%E6%97%B6%E6%B3%A8%E6%84%8F%E4%BA%8B%E9%A1%B9/">
     *     Spring Data Redis与Lettuce 使用 pipeline 时注意事项</a>
     * @see <a href="https://github.com/spring-projects/spring-data-redis/issues/1581">
     *     Lettuce pipelining behaviour is different than Jedis pipelining [DATAREDIS-1011] </a>
     * @see <a href="https://docs.spring.io/spring-data/redis/docs/2.2.11.RELEASE/reference/html/#pipeline">
     *     spring-data-redis pipeline</a>
     */
    @Test
    public void lettuce(){
        String key = "lettuce:pipeline";
        int limit = 400;

        List<Object> list = _stringRedisTemplate.executePipelined(new RedisCallback<Object>() {
            @Override
            public Object doInRedis(RedisConnection connection) throws DataAccessException {
                StringRedisConnection stringRedisConn = (StringRedisConnection)connection;

                int num = 0;
                while (num++ < limit) {
                    stringRedisConn.incr(key.getBytes());
                    stringRedisConn.expire(key, 10);
                }
                return null;
            }
        });

        System.out.printf("exec: incr, key: %s, result: %s \r\n", key, StringUtils.join(list, ","));
    }

    /**
     * `incr lettuce:manual` --RESP--> 45, `*2\r\n$4\r\nINCR\r\n$14\r\nlettuce:manual\r\n` <br/>
     *
     * <p>
     * 通过wireshark抓包可知：
     *  lettuce 一般会按 604(bytes)发送N次，但是也可能在这之后再按 1724/2284 发送几次。<br/>
     *  <b>lettuce pipeline 保证每次 PSH 中的RESP协议都是完整，而jedis强制按8192(bytes)分次发送</b>
     * </p>
     *
     * VTODO 2021-05-17 lettuce 分组策略源码位置？
     *
     * @see <a href="https://lettuce.io/core/release/reference/index.html#_pipelining_and_command_flushing">
     *     lettuce _pipelining_and_command_flushing</a>
     */
    @Test
    public void lettucePipeline(){
        String key = "lettuce:pipeline:manual";
        int limit = 400;

        LettuceConnection lettuceConnection = (LettuceConnection) _stringRedisTemplate.getConnectionFactory().getConnection();
        RedisClusterAsyncCommands<byte[], byte[]> commands = lettuceConnection.getNativeConnection();

        // 如果想达到打包发送请求的效果（类似jedis-pipeline），需要设置`autoFlushCommands=false`
        // disable auto-flushing
        commands.setAutoFlushCommands(false);
        commands.setTimeout(Duration.ofMinutes(10));

        // perform a series of independent calls
        List<RedisFuture<Long>> futures = Lists.newArrayList();
        for (int i = 0; i < limit; i++) {
            futures.add(commands.incr(key.getBytes()));
        }

        // 因为`autoFlushCommands=false`，所以需要手动提交命令
        // write all commands to the transport layer
        commands.flushCommands();

        List<Object> result = Lists.newArrayList();
        futures.forEach(e -> {
            try {
                result.add(e.get());
            } catch (InterruptedException | ExecutionException ex) {
                ex.printStackTrace();
            }
        });

        // later
        lettuceConnection.close();

        System.out.printf("exec: lettuce-get, key: %s, result: %s \r\n", key, StringUtils.join(result, ","));

    }
}
