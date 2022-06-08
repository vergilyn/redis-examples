package com.vergilyn.examples.commons.serializer;

import com.alibaba.fastjson.parser.Feature;
import com.alibaba.fastjson.serializer.SerializerFeature;
import com.alibaba.fastjson.support.config.FastJsonConfig;
import com.alibaba.fastjson.support.spring.FastJsonRedisSerializer;
import org.springframework.data.redis.serializer.Jackson2JsonRedisSerializer;
import org.springframework.data.redis.serializer.RedisSerializer;

public class RedisSerializerFactory {

	public static <T> RedisSerializer<T> jackson(){
		return jacksonRedisSerializer();
	}

	public static <T> RedisSerializer<T> fastjson(){
		return getFastJsonRedisSerializer();
	}

	private static <T> Jackson2JsonRedisSerializer<T> jacksonRedisSerializer() {
		// Jackson2JsonRedisSerializer jackson2JsonRedisSerializer = new Jackson2JsonRedisSerializer(Object.class);
		// ObjectMapper om = new ObjectMapper();
		// om.setVisibility(PropertyAccessor.ALL, Visibility.ANY);
		// om.enableDefaultTyping(DefaultTyping.NON_FINAL);
		// jackson2JsonRedisSerializer.setObjectMapper(om);
		// return jackson2JsonRedisSerializer;
		throw new UnsupportedOperationException();
	}

	private static <T> FastJsonRedisSerializer<T> getFastJsonRedisSerializer() {
		FastJsonRedisSerializer<T> serializer = new FastJsonRedisSerializer(Object.class);
		FastJsonConfig fastJsonConfig = new FastJsonConfig();
		fastJsonConfig.setSerializerFeatures(new SerializerFeature[]{SerializerFeature.WriteClassName});
		fastJsonConfig.setFeatures(new Feature[]{Feature.SupportAutoType});
		serializer.setFastJsonConfig(fastJsonConfig);
		return serializer;
	}
}
