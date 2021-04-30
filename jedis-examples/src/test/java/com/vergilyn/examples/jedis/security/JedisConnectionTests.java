package com.vergilyn.examples.jedis.security;

import java.util.List;
import java.util.Map;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.ThreadFactory;
import java.util.concurrent.atomic.AtomicInteger;

import com.google.common.collect.Lists;
import com.google.common.collect.Maps;
import com.vergilyn.examples.jedis.AbstractJedisTests;

import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.RepeatedTest;
import redis.clients.jedis.Jedis;
import redis.clients.jedis.exceptions.JedisConnectionException;
import redis.clients.jedis.exceptions.JedisDataException;

import static org.assertj.core.api.Assertions.assertThat;

/**
 *
 * @author vergilyn
 * @since 2021-04-30
 */
public class JedisConnectionTests extends AbstractJedisTests {

	private static final String KEY = "jedis:connection:concurrent";

	@BeforeAll
	public static void beforeAll(){
		Map<String, String> data = Maps.newHashMap();
		for (int i = 0; i < 10; i++) {
			data.put(i + "", i + "");
		}

		Jedis jedis = jedis();
		jedis.hmset(KEY, data);
		jedis.close();
	}

	/**
	 * 多个线程之间不能共享connection，原因：<br/>
	 *   如果共用1个连接，那么返回的结果无法保证被哪个进程处理。
	 *   持有连接的进程理论上都可以对这个连接进行读写，这样数据就发生错乱了。
	 *   或者抛出异常：<br/>
	 *   1) {@linkplain JedisConnectionException}, message: Unexpected end of stream.message: Unexpected end of stream. <br/>
	 *   2) {@linkplain JedisDataException}, message: ERR Protocol error: ...
	 *
	 * @see <a href="http://blog.csdn.net/hao508506/article/details/53039345">redis实践之请勿踩多进程共用一个实例连接的坑</a>
	 */
	@RepeatedTest(5)
	public void concurrent(){
		ExecutorService threadPool = Executors.newFixedThreadPool(10, new ThreadFactory() {
			private final AtomicInteger index = new AtomicInteger(0);
			@Override
			public Thread newThread(Runnable r) {
				Thread thread = new Thread(r);
				thread.setName(JedisConnectionTests.class.getSimpleName() + "-" + index.getAndIncrement());
				return thread;
			}
		});

		Jedis singleJedis = jedis();
		int times = 10;
		Map<String, String> errors = Maps.newConcurrentMap();
		List<Future<?>> futures = Lists.newArrayListWithCapacity(times);
		for (int i = 0; i < times; i++) {
			Future<?> future = threadPool.submit(() -> {
				String thread = Thread.currentThread().getName();
				String field = String.valueOf(thread.charAt(thread.length() - 1));

				// 多线程共享connection会出现数据混乱, 或exception.
				String hget = "";
				try {

					hget = singleJedis.hget(KEY, field);

					// 当每个线程拥有自己的connection时, 不会出现数据混乱.
					// hget = jedis().hget(KEY, field);
				}catch (Exception e){
					hget = e.getMessage();

					System.out.printf("[Exception] >>>> %s, message: %s \n",
							e.getClass().getName(), e.getMessage());
				}

				if (!hget.equals(field)) {
					errors.put(field, String.format("[error] thread: %s, field: %s, hget: %s", thread, field, hget));
				}
			});
			futures.add(future);
		}

		futures.forEach(future -> {
			try {
				future.get();
			} catch (Exception e) {
				e.printStackTrace();
			}
		});

		singleJedis.close();
		assertThat(errors).isNotEmpty();

		errors.forEach((key, value) -> {
			System.out.printf("index: %s >>>> %s \n", key, value);
		});
	}

}
