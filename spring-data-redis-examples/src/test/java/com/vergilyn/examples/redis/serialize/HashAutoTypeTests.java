package com.vergilyn.examples.redis.serialize;

import com.vergilyn.examples.redis.AbstractRedisClientTests;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.junit.jupiter.api.Test;
import org.springframework.data.redis.core.HashOperations;
import org.springframework.data.redis.core.RedisTemplate;

import javax.annotation.Resource;
import java.io.Serializable;
import java.time.LocalDateTime;

public class HashAutoTypeTests extends AbstractRedisClientTests {

    @Resource
    private RedisTemplate<String, PersonInfo> redisTemplate;

    @Test
    void test(){
        HashOperations<String, String, PersonInfo> hashOperations = redisTemplate.opsForHash();

        PersonInfo p1 = new PersonInfo(1, "A", LocalDateTime.now());
        PersonInfo p2 = new PersonInfo(2, "B", LocalDateTime.now());

        String key = "test:redis:type";
        hashOperations.put(key, p1.name, p1);
        hashOperations.put(key, p2.name, p2);
    }

    @Data
    @NoArgsConstructor
    private static class PersonInfo implements Serializable {
        private Integer id;
        private String name;
        private LocalDateTime localDateTime;

        public PersonInfo(Integer id, String name, LocalDateTime localDateTime) {
            this.id = id;
            this.name = name;
            this.localDateTime = localDateTime;
        }
    }
}
