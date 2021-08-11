package com.vergilyn.examples.redis.bitmap;

import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.List;

import com.vergilyn.examples.redis.AbstractRedisClientTests;

import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.MethodOrderer;
import org.junit.jupiter.api.Order;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;
import org.junit.jupiter.api.TestMethodOrder;
import org.springframework.data.redis.connection.BitFieldSubCommands;
import org.springframework.data.redis.core.RedisCallback;

@TestInstance(TestInstance.Lifecycle.PER_CLASS)
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
public class BitmapCommandsTests extends AbstractRedisClientTests {

	private static final String KEY = "bit";

	@BeforeAll
	private void beforeAll(){
		stringRedisTemplate.delete(KEY);
	}

	@Test
	@Order(1)
	public void setBit(){
		stringRedisTemplate.opsForValue().setBit(KEY, 0, false);
		stringRedisTemplate.opsForValue().setBit(KEY, 1, false);
		stringRedisTemplate.opsForValue().setBit(KEY, 2, true);
		stringRedisTemplate.opsForValue().setBit(KEY, 3, true);
		stringRedisTemplate.opsForValue().setBit(KEY, 4, true);
	}

	@Test
	@Order(2)
	public void getBit(){
		for (int i = 0; i < 5; i++) {
			System.out.printf("[getBit] >>>> offset: %d, value: %b \n",
					i, stringRedisTemplate.opsForValue().getBit(KEY, i));
		}
	}

	@Test
	@Order(3)
	public void bitCount(){
		Long bitCount = stringRedisTemplate.execute(
				(RedisCallback<Long>) connection -> connection.bitCount(KEY.getBytes(StandardCharsets.UTF_8)));

		System.out.printf("[bitCount] >>>> count: %d \n", bitCount);
	}

	@Test
	@Order(4)
	public void bitField(){
		BitFieldSubCommands bitFieldSubCommands = BitFieldSubCommands.create()
				.get(BitFieldSubCommands.BitFieldType.INT_8).valueAt(0L);

		List<Long> bitFieldGet = stringRedisTemplate.opsForValue().bitField(KEY, bitFieldSubCommands);

		System.out.printf("[bitField] >>>> result: %s \n", Arrays.toString(bitFieldGet.toArray(new Long[0])));
	}
}
