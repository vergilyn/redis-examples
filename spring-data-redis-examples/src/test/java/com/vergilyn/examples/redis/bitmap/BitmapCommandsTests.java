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

/**
 * redis的Bitmap是特殊的string，<b>低位在左边，高位在右边。与计算机的相反</b>
 * 例如"01"，低位是0，高位是1。 <br/>
 * 从而需要小心 {@linkplain #bitField()} 返回值陷阱。
 *
 * @author dingmaohai
 * @version v1.0
 * @since 2021/08/13 18:12
 */
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
public class BitmapCommandsTests extends AbstractRedisClientTests {

	private static final String KEY = "spring-data:bitmap";

	@BeforeAll
	private void beforeAll(){
		stringRedisTemplate.delete(KEY);

		// redis中保存的字符串是"00111"，左边低位，右边高位。
		stringRedisTemplate.opsForValue().setBit(KEY, 0, false);
		stringRedisTemplate.opsForValue().setBit(KEY, 1, false);
		stringRedisTemplate.opsForValue().setBit(KEY, 2, true);
		stringRedisTemplate.opsForValue().setBit(KEY, 3, true);
		stringRedisTemplate.opsForValue().setBit(KEY, 4, true);

		stringRedisTemplate.opsForValue().setBit(KEY, 19, true);
	}

	@Test
	@Order(1)
	public void setBit(){
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

	/**
	 * `BITFIELD get` 最多支持返回 i63(有符号63bit) 或者 u64(无符号64bit) 的整型。
	 * 所以通过`BITFIELD get`最多只能取64bits的数据。
	 *
	 * <p></p>
	 * <b>特别注意：</b>
	 * <pre>
	 * 例如redis 保存的bitmap字符串是“01”
	 * 如果是`GET u8 0`，因为位数不够，所以由redis高位补0（高位在右边），变成“0100 0000”，所以返回整数“64”。
	 * 如果是`GET u3 0`，先由redis补位“010”，然后由通讯传输协议(怎么描述比较好?) 补齐Integer需要的8bit （高位在左边）变成“0000 0010”，所以返回整数“2”。
	 * （上面有些表达术语不对，但整体思路没错）
	 * </pre>
	 */
	@Test
	@Order(4)
	public void bitField(){
		BitFieldSubCommands bitFieldSubCommands = BitFieldSubCommands.create()
				.get(BitFieldSubCommands.BitFieldType.unsigned(2)).valueAt(0L);

		List<Long> bitFieldGet = stringRedisTemplate.opsForValue().bitField(KEY, bitFieldSubCommands);

		System.out.printf("[bitField] >>>> result: %s \n", Arrays.toString(bitFieldGet.toArray(new Long[0])));
	}

	/**
	 * 因为redis中的 Bitmap是特殊的 String，所以可以用 String 的`GET key`获取bitmap完整的值。
	 *
	 * <p/>
	 * <a href="https://redis.io/commands/setbit#pattern-accessing-the-entire-bitmap">Pattern: accessing the entire bitmap</a>
	 * <pre>
	 * Because Redis' strings are binary-safe, a bitmap is trivially encoded as a bytes stream.
	 * The first byte of the string corresponds to offsets 0..7 of the bitmap,
	 * the second byte to the 8..15 range, and so forth.
	 * </pre>
	 * 另外，有文章说redis返回的是 16进制字符串（没在redis.io找到相应的描述），
	 * 但实际`SETBIT xx [1, 18, 32] 1`，`GET xx`返回是"@\x00 \x00\x80"，也不完全是 16进制字符串。<br/>
	 *
	 * 解决bitSet java redis 字节顺序问题：
	 * redis存储的结构是从左到右，java的BitSet事从右到左
	 *
	 * @see <a href="https://segmentfault.com/a/1190000008188655">redis 使用 get 命令读取 bitmap 类型的数据</a>
	 */
	@Test
	@Order(5)
	public void getFullBit(){
		String str = stringRedisTemplate.opsForValue().get(KEY);
		byte[] bytes = str.getBytes(StandardCharsets.UTF_8);

		// 保持跟redis一致 左到右 等价于 低位到高位。
		StringBuilder binaryBuilder = new StringBuilder();
		int len = bytes.length * 8;
		int[] result = new int[len];
		for (int i = 0; i < len; i++) {
			result[i] = (bytes[i / 8] & (1 << (7 - (i % 8)))) != 0 ? 1 : 0;
			binaryBuilder.append(result[i]);
		}

		System.out.println("binary >>>> " + binaryBuilder);
	}
}
