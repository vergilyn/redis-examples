package com.vergilyn.examples.data.redisson.exception;

import com.alibaba.fastjson.JSON;
import com.google.common.collect.Maps;
import com.vergilyn.examples.data.redisson.AbstractRedissonDataApplicationTests;
import lombok.SneakyThrows;
import lombok.extern.slf4j.Slf4j;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.redisson.api.RFuture;
import org.redisson.command.RedisExecutor;
import org.redisson.misc.RPromise;

import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

@Slf4j
public class RedisNettyThreadsExceptionTests extends AbstractRedissonDataApplicationTests {

	private final String key = "test:NettyThreadsException";

	@BeforeEach
	private void beforeEach(){
		Map<String, String> values = Maps.newHashMap();
		for (int i = 0; i < 20; i++) {
			values.put(i + "", UUID.randomUUID().toString());
		}
		_stringRedisTemplate.opsForHash().putAll(key,values);
	}

	/**
	 * `Command still hasn't been written into connection!(命令还没有写入连接) Try to increase nettyThreads setting.`
	 *
	 * <p> 按error-msg的理解，client无法将command写入到connect。（connect正常？）
	 *
	 * <p> <b>2022-07-21，已确定，是由于网络波动造成。</b>
	 *
	 * <p> {@link RedisExecutor#execute()}、{@link RedisExecutor#scheduleRetryTimeout(RFuture, RPromise)}
	 * <pre>
	 * org.springframework.dao.QueryTimeoutException: Command still hasn't been written into connection! Try to increase nettyThreads setting.
	 *   Payload size in bytes: 0. Node source: NodeSource [slot=0, addr=null, redisClient=null, redirect=null, entry=null],
	 *   connection: RedisConnection@1265498735 [redisClient=[addr=redis://127.0.0.1:6379],
	 *      channel=[id: 0x9e54c5a6, L:/127.0.0.2:34536 - R:127.0.0.1/127.0.0.3:6379],
	 *   currentCommand=CommandData [promise=RedissonPromise [promise=ImmediateEventExecutor$ImmediatePromise@5b98bd5a(failure: java.util.concurrent.CancellationException)],
	 *   command=(GET), params=[[...]],
	 *   codec=org.redisson.client.codec.ByteArrayCodec]], command: (HVALS),
	 *   params: [[...]] after 3 retry attempts;
	 *   nested exception is org.redisson.client.RedisTimeoutException:
	 *      Command still hasn't been written into connection! Try to increase nettyThreads setting. Payload size in bytes: 0.
	 *      Node source: NodeSource [slot=0, addr=null, redisClient=null, redirect=null, entry=null],
	 *      connection: RedisConnection@1265498735 [redisClient=[addr=redis://127.0.0.1:6379],
	 *      channel=[id: 0x9e54c5a6, L:/127.0.0.2:34536 - R:127.0.0.1/127.0.0.3:6379],
	 *      currentCommand=CommandData [promise=RedissonPromise [promise=ImmediateEventExecutor$ImmediatePromise@5b98bd5a(failure: java.util.concurrent.CancellationException)],
	 *      command=(GET), params=[[...]], codec=org.redisson.client.codec.ByteArrayCodec]],
	 *      command: (HVALS), params: [[...]] after 3 retry attempts
	 *         at org.redisson.spring.data.connection.RedissonExceptionConverter.convert(RedissonExceptionConverter.java:48)
	 *         at org.redisson.spring.data.connection.RedissonExceptionConverter.convert(RedissonExceptionConverter.java:35)
	 *         at org.springframework.data.redis.PassThroughExceptionTranslationStrategy.translate(PassThroughExceptionTranslationStrategy.java:44)
	 *         at org.redisson.spring.data.connection.RedissonConnection.transform(RedissonConnection.java:195)
	 *         at org.redisson.spring.data.connection.RedissonConnection.syncFuture(RedissonConnection.java:190)
	 *         at org.redisson.spring.data.connection.RedissonConnection.sync(RedissonConnection.java:356)
	 *         at org.redisson.spring.data.connection.RedissonConnection.read(RedissonConnection.java:737)
	 *         at org.redisson.spring.data.connection.RedissonConnection.hVals(RedissonConnection.java:1436)
	 *         at org.springframework.data.redis.core.DefaultHashOperations.lambda$values$11(DefaultHashOperations.java:219)
	 *         at org.springframework.data.redis.core.RedisTemplate.execute(RedisTemplate.java:228)
	 *         at org.springframework.data.redis.core.RedisTemplate.execute(RedisTemplate.java:188)
	 *         at org.springframework.data.redis.core.AbstractOperations.execute(AbstractOperations.java:96)
	 *         at org.springframework.data.redis.core.DefaultHashOperations.values(DefaultHashOperations.java:219)
	 *         at com.zmn.mcs.cache.impl.rule.RuleCacheImpl.listConfigByRuleCode(RuleCacheImpl.java:72)
	 * </pre>
	 */
	@SneakyThrows
	@ParameterizedTest
	@ValueSource(ints = {128})
	public void concurrent(int threads){
		ExecutorService executorService = Executors.newFixedThreadPool(threads);

		for (int i = 0; i < threads; i++) {
			executorService.submit(new Runnable() {
				@Override
				public void run() {
					try {
						hashValues();
					}catch (Exception e){
						e.printStackTrace();
					}
				}
			});
		}

		TimeUnit.MINUTES.sleep(1);
	}


	@Test
	public void hashValues(){
		List<String> vs = _stringRedisTemplate.<String, String>opsForHash().values(key);
		log.info(" hash-values >>> " + JSON.toJSONString(vs));
	}

	public static void main(String[] args) {

		byte[] bytes = "test:NettyThreadsException".getBytes(StandardCharsets.UTF_8);
		for (byte aByte : bytes) {
			System.out.print(aByte + ", ");
		}
	}
}
