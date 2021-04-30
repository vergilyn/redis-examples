package com.vergilyn.examples.jedis.transaction;

import java.util.List;
import java.util.Map;

import com.vergilyn.examples.jedis.AbstractJedisTests;

import org.junit.jupiter.api.Test;
import redis.clients.jedis.Jedis;
import redis.clients.jedis.Transaction;
import redis.clients.jedis.exceptions.JedisDataException;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.catchThrowable;

/**
 * <a href="http://www.runoob.com/redis/redis-transactions.html">Redis 事务</a>
 * <p>
 *     redis事务可以理解为一个打包的批量执行脚本，但批量指令并非原子化的操作，
 *     中间某条指令的失败不会导致前面已做指令的回滚，也不会造成后续的指令不做。
 * </p>
 * @author vergilyn
 * @since 2021-04-30
 */
public class JedisTransactionTest extends AbstractJedisTests {

	@Test
	public void tx(){
		String key = this.getClass().getSimpleName();
		Jedis jedis = jedis();
		jedis.del(key);

		Transaction tx = jedis.multi();
		tx.hset(key, "A", "1"); // command-01
		tx.get(key);                        // command-02
		tx.hset(key, "C", "3"); // command-03

		List<Object> results = tx.exec();

		JedisDataException throwable = (JedisDataException) results.get(1);

		assertThat(throwable).isOfAnyClassIn(JedisDataException.class);
		assertThat(throwable).hasMessageContaining("WRONGTYPE Operation against a key holding the wrong kind of value");

		// 虽然 c02错误，但c01和c02都会执行。
		Map<String, String> value = jedis().hgetAll(key);
		assertThat(value).containsOnlyKeys("A", "C");

		throwable.printStackTrace();

		jedis.close();
	}

	/**
	 * 这其实是jedis-API抛出的异常，并没有提交redis-command到redis，所以key才不存在。
	 */
	@Test
	public void errorTestCode(){
		String key = this.getClass().getSimpleName();
		Jedis jedis = jedis();
		Transaction tx = jedis.multi();

		final Throwable throwable = catchThrowable(() -> {
			tx.hset(key, "A", "1");
			// exception >> redis.clients.jedis.exceptions.JedisDataException: value sent to redis cannot be null
			tx.hset(key, "B", null);
			tx.hset(key, "C", "1");

			List<Object> results = tx.exec();
		});

		assertThat(throwable).isOfAnyClassIn(JedisDataException.class);
		assertThat(throwable).hasMessageContaining("value sent to redis cannot be null");

		Boolean exists = jedis().exists(key);
		assertThat(exists).isFalse();

		throwable.printStackTrace();

		jedis.close();
	}
}
