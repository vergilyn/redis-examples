package com.vergilyn.examples.config;

import com.google.common.primitives.Ints;

import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.data.redis.RedisProperties;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import redis.clients.jedis.JedisPool;
import redis.clients.jedis.JedisPoolConfig;

/**
 * @author VergiLyn
 * @date 2019-06-14
 */
@Configuration
@EnableConfigurationProperties(RedisProperties.class)
@Slf4j
public class JedisConfiguration {

    @Bean
    public JedisPool jedisPool(RedisProperties properties){
        RedisProperties.Pool pool = properties.getLettuce().getPool();

        JedisPoolConfig jedisPoolConfig = new JedisPoolConfig();
        jedisPoolConfig.setMaxIdle(pool.getMaxIdle());
        jedisPoolConfig.setMinIdle(pool.getMinIdle());
        jedisPoolConfig.setMaxWaitMillis(pool.getMaxWait().toMillis());
        jedisPoolConfig.setMaxTotal(pool.getMaxActive());
        jedisPoolConfig.setTestOnBorrow(true);

        JedisPool jedisPool = new JedisPool(jedisPoolConfig, properties.getHost(), properties.getPort(), Ints.checkedCast(properties.getTimeout().toMillis()));

        return jedisPool;
    }
}
