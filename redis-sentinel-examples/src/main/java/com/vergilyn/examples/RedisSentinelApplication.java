package com.vergilyn.examples;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.CommandLineRunner;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.data.redis.core.StringRedisTemplate;

@SpringBootApplication
public class RedisSentinelApplication implements CommandLineRunner {
    @Autowired
    private StringRedisTemplate redisTemplate;

    public static void main(String[] args) {
        SpringApplication.run(RedisSentinelApplication.class, args);
    }

    @Override
    public void run(String... args) throws Exception {
        redisTemplate.boundValueOps("sentinel").set("sentinel hello!");
    }
}
