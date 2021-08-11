package com.vergilyn.examples.redis.bitmap;

import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.BitSet;

import com.vergilyn.examples.redis.AbstractJedisTests;

import org.junit.jupiter.api.Test;
import redis.clients.jedis.Jedis;

/**
 *
 * @author vergilyn
 * @since 2021-08-09
 *
 * @see <a href="https://redis.io/commands/setbit">commands - Bitmap</a>
 */
public class BitmapBasicTests extends AbstractJedisTests {

	@Test
	public void get(){
		final Jedis jedis = jedis();


		String key = "key:bitmap";
		jedis.del(key);
		jedis.setbit(key, 0, false);
		jedis.setbit(key, 1, false);
		jedis.setbit(key, 2, true);
		jedis.setbit(key, 3, true);
		jedis.setbit(key, 4, true);

		// 返回的是 16进制 字符串
		String bitmapValue = jedis.get(key);

		System.out.println("bitmapValue >>>> " + bitmapValue);

		byte[] bytes = bitmapValue.getBytes(StandardCharsets.UTF_8);
		for (byte aByte : bytes) {
			System.out.println(String.valueOf(aByte));
		}

		System.out.println("byteToBinary >>>> " + Arrays.toString(byteToBinary(bytes)));
	}

	@Test
	public void binary(){
		String exceptedBinary = "0011 1000";
		String str = "42";
		// [56]
		byte[] bytes = str.getBytes(StandardCharsets.UTF_8);

		// 不能直接使用`BitSet.valueOf()`，得到的结果是错误的
		BitSet incorrect = BitSet.valueOf(bytes);
		int[] incorrectAscBinary = byteToBinary(bytes);
		System.out.println("incorrect >>>> " + Arrays.toString(incorrectAscBinary));

		BitSet correct = fromByteArrayReverse(bytes);
		int[] correctAscBinary = byteToBinary(bytes);
		String correctBinary = bitSetToBinary(correct);
		System.out.println("correct   >>>> " + correctBinary);
	}

	/**
	 *
	 * @return 从`0..n` 低位到高位
	 * @see #fromByteArrayReverse(byte[])
	 */
	private static int[] byteToBinary(final byte[] bytes) {
		int len = bytes.length * 8;
		int[] result = new int[len];
		for (int i = 0; i < len; i++) {
			result[i] = (bytes[i / 8] & (1 << (7 - (i % 8)))) != 0 ? 1 : 0;
		}
		return result;
	}

	/**
	 * <a href="https://redis.io/commands/setbit#pattern-accessing-the-entire-bitmap">Pattern: accessing the entire bitmap</a>
	 * <pre>
	 * Because Redis' strings are binary-safe, a bitmap is trivially encoded as a bytes stream.
	 * The first byte of the string corresponds to offsets 0..7 of the bitmap,
	 * the second byte to the 8..15 range, and so forth.
	 * </pre>
	 *
	 * 解决bitSet java redis 字节顺序问题：
	 * redis存储的结构是从左到右，java的BitSet事从右到左
	 *
	 */
	private static BitSet fromByteArrayReverse(final byte[] bytes) {
		BitSet bits = new BitSet();
		for (int i = 0, len = bytes.length * 8; i < len; i++) {
			if ((bytes[i / 8] & (1 << (7 - (i % 8)))) != 0) {
				bits.set(i);
			}
		}
		return bits;
	}

	/**
	 * BitSet转二进制(字符串)
	 */
	public static String bitSetToBinary(BitSet bitSet) {
		StringBuilder binary = new StringBuilder();
		for (int i = 0, len = bitSet.length(); i < len; i++) {
			if (bitSet.get(i)) {
				binary.append("1");
			} else {
				binary.append("0");
			}
		}

		return binary.toString();
	}
}
