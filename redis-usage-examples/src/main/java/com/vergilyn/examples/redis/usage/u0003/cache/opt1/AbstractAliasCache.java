package com.vergilyn.examples.redis.usage.u0003.cache.opt1;

import java.util.function.BiConsumer;
import java.util.function.Supplier;

import org.springframework.data.redis.core.BoundValueOperations;
import org.springframework.data.redis.core.RedisTemplate;

/**
 *
 * @author vergilyn
 * @since 2021-04-30
 */
public abstract class AbstractAliasCache<V> {

    protected final RedisTemplate<String, V> redisTemplate;

    public AbstractAliasCache(RedisTemplate<String, V> redisTemplate) {
        this.redisTemplate = redisTemplate;
    }

    protected final V getByCache(String key, Supplier<V> query, BiConsumer<RedisTemplate<String, V>, V> writeCache){
        BoundValueOperations<String, V> valueOps = redisTemplate.boundValueOps(key);
        V value = valueOps.get();
        if (value != null){
            return value;
        }

        value = query.get();
        if (value == null){
            return null;
        }

        writeCache.accept(redisTemplate, value);

        return value;
    }
}
