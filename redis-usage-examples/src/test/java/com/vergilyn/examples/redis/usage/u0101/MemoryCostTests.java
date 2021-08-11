package com.vergilyn.examples.redis.usage.u0101;

import java.util.Set;

import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;

/**
 * 随机生成的18位user-id过于分散，导致以下方案都未减少key，不如直接 string。
 *
 * @author vergilyn
 * @since 2021-07-27
 */
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
public class MemoryCostTests extends AbstractUseIdsTests {
	private static Set<Long> _shuffle_user_ids;

	@BeforeAll
	public void beforeAll(){
		_shuffle_user_ids = generator(100_000L);
	}

	/**
	 * insert-COST: 4min 26sec
	 * key_count: 100000
	 * used_memory_human: 7.99M
	 */
	@Test
	public void string(){
		String keyFormat = "user_id:string:%s";
		for (Long userId : _shuffle_user_ids) {

			// key存在即可，value `0/1` 意义不大。
			_stringRedisTemplate.opsForValue()
					.set(String.format(keyFormat, userId), "1");
		}
	}

	/**
	 * insert-COST: 4min 32sec
	 * key_count: 100000
	 * used_memory_human: 11.05M
	 *
	 * 随机生成的userId过于分散，每个hash-key都只包含一个 field-value，完全等价于 string-key。
	 *
	 * <pre>
	 *   # Hashes are encoded using a memory efficient data structure when they have a
	 *   # small number of entries, and the biggest entry does not exceed a given
	 *   # threshold. These thresholds can be configured using the following directives.
	 *   hash-max-ziplist-entries 512
	 *   hash-max-ziplist-value 64
	 * </pre>
	 */
	@Test
	public void hash(){
		String keyFormat = "user_id:hash:%s";
		String key;
		for (Long userId : _shuffle_user_ids) {
			key = String.format(keyFormat, userId / 512);

			// key&field存在即可，value `0/1` 意义不大。
			_stringRedisTemplate.opsForHash().put(key, userId + "", "1");
		}
	}

	/**
	 * insert-COST: 4min 25sec
	 * key_count: 100000
	 * used_memory_human: 10.28M
	 *
	 * 随机生成的userId过于分散，每个set只包含1个member
	 *
	 * <pre>
	 *   # Sets have a special encoding in just one case: when a set is composed
	 *   # of just strings that happen to be integers in radix 10 in the range
	 *   # of 64 bit signed integers.
	 *   # The following configuration setting sets the limit in the size of the
	 *   # set in order to use this special memory saving encoding.
	 *   set-max-intset-entries 512
	 * </pre>
	 */
	@Test
	public void set(){
		String keyFormat = "user_id:set:%s";
		String key;
		for (Long userId : _shuffle_user_ids) {
			key = String.format(keyFormat, userId / 512);

			// member 可以优化成： member-offset = userId - (userId / 512) * 512
			_stringRedisTemplate.opsForSet().add(key, userId + "");
		}
	}

	/**
	 * insert-COST: 4min 36sec
	 * limit: 18 bytes * 2
	 * key_count: 100000
	 * used_memory_human: 11.46M
	 *
	 */
	@Test
	public void bitmap(){
		String keyFormat = "user_id:bitmap:%s";
		String key;
		long bucket, offset;
		// 1MB = 1024 * 1024 * 8 = 8,388,608 Bits
		// 18bytes = 18 * 8 = 144
		long bitmapLimit = 144 * 2;
		for (Long userId : _shuffle_user_ids) {
			bucket = userId / bitmapLimit;
			key = String.format(keyFormat, bucket);

			offset = userId - bucket * bitmapLimit;

			_stringRedisTemplate.opsForValue().setBit(key, offset, true);
		}
	}
}
