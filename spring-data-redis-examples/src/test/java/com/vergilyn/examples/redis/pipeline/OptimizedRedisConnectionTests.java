package com.vergilyn.examples.redis.pipeline;

import com.google.common.collect.Lists;
import com.google.common.collect.Maps;
import com.vergilyn.examples.redis.AbstractRedisClientTests;
import io.lettuce.core.RedisFuture;
import io.lettuce.core.cluster.api.async.RedisClusterAsyncCommands;
import lombok.SneakyThrows;
import org.apache.commons.lang3.RandomUtils;
import org.junit.jupiter.api.TestInstance;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.dao.DataAccessException;
import org.springframework.data.redis.connection.RedisConnection;
import org.springframework.data.redis.connection.lettuce.LettuceConnection;
import org.springframework.data.redis.core.RedisCallback;
import org.springframework.data.redis.core.types.RedisClientInfo;

import java.time.Duration;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

@TestInstance(TestInstance.Lifecycle.PER_CLASS)
public class OptimizedRedisConnectionTests extends AbstractRedisClientTests {

	public static final String TYPE_PIPELINE_INCORRECT = "pipeline-incorrect";

	public static final String TYPE_PIPELINE_CORRECT = "pipeline-correct";

	public static final String TYPE_ONE_BY_ONE = "one-by-one";

	private final int _limit = 1000;

	// @BeforeAll
	public void beforeAll(){
		Map<String, String> blacklistMap = Maps.newHashMapWithExpectedSize(_limit);
		for (int i = 0; i < _limit; i++) {
			blacklistMap.put(buildKey(i), buildUser(i));
		}

		// 1次 PSH 请求。
		stringRedisTemplate.opsForValue().multiSet(blacklistMap);

		// monitorClientList();
	}

	/**
	 *
	 * <h3>{@link #TYPE_ONE_BY_ONE}</h3>
	 * 可能也只消耗了 1个connection（连接复用），但是会有 N次 交互应答。
	 * (因为 spring-data-redis 默认使用的是 lettuce)
	 *
	 * <p>
	 * <h3>{@link #TYPE_PIPELINE_INCORRECT}</h3>
	 * 这种写法不一定能达到期望的pipeline效果，可能还是会有 N次交互应答。
	 * <p> 如果是 lettuce，还是会是 N次交互和应答。
	 * <p> 如果是 jedis，`spring.redis.client-type = jedis`（高版本支持此配置），可以达到期望的pipeline效果。
	 *
	 * <p>{@link #TYPE_PIPELINE_CORRECT}</h3>
	 * 针对lettuce，可以 <b>硬编码</b>的方式实现期望的pipeline。参考：{@link #lettucePipeline(List)}
	 * <br/>
	 *
	 * @see <a href="https://github.com/lettuce-io/lettuce-core/wiki/Pipelining-and-command-flushing">
	 *          Lettuce, Pipelining and command flushing</a>
	 *
	 * @see <a href="https://docs.spring.io/spring-data/data-redis/docs/2.2.11.RELEASE/reference/html/#pipeline">
	 *          pipeline, spring-data-redis, 2.2.11.RELEASE</a>
	 *
	 * @see <a href="https://github.com/lettuce-io/lettuce-core/wiki/Pipelining-and-command-flushing">
	 *          Lettuce，Pipelining and command flushing</a>
	 */
	@ParameterizedTest()
	@ValueSource(strings = { TYPE_PIPELINE_INCORRECT })
	public void test(String type){
		int blackNums = _limit / 6;

		List<String> userList = Lists.newArrayListWithCapacity(blackNums);
		for (int i = 0; i < blackNums; i++) {

			String key = buildKey(RandomUtils.nextInt(0, _limit));
			if (!userList.contains(key)){
				userList.add(key);
			}
		}

		userList.add(buildKey(409839));
		userList.add(buildKey(839163));
		userList.add(buildKey(10080));

		Collections.shuffle(userList);

		if (TYPE_ONE_BY_ONE.equals(type)){
			oneByOne(userList);

		}else if (TYPE_PIPELINE_INCORRECT.equals(type)){
			incorrectPipeline(userList);

		}else if (TYPE_PIPELINE_CORRECT.equals(type)){
			correctPipeline(userList);
		}
	}

	private void correctPipeline(List<String> userList){
		// 避免 pipeline 阻塞其它命令，不宜一次性执行太多。
		List<List<String>> partitions = Lists.partition(userList, 50);

		Map<String, Boolean> resultMap = Maps.newConcurrentMap();

		for (List<String> users : partitions) {
			resultMap.putAll(lettucePipeline(users));
		}

		System.out.printf("[vergilyn] >>>> user.size: %d, resultMap.size: %d \n", userList.size(), resultMap.size());
		for (Map.Entry<String, Boolean> entry : resultMap.entrySet()) {
			System.out.printf("%s >>>> is-blacklist: %b \n", entry.getKey(), entry.getValue());
		}
	}

	/**
	 * 通过手写的方式，可以实现类似 jedis-pipeline。
	 * <p> 1. 通过wireshark可知，每次PSH大小不一定，最多748
	 * <p> 2. 是否需要显示调用 {@link LettuceConnection#close()}？
	 * <p> 3. 是否需要显示调用 {@link LettuceConnection#openPipeline()}？
	 *
	 * <p>
	 * <p> 对于`2/3`，貌似 调用or不调用 表现出来的效果都是一样的。
	 *
	 * @param users
	 * @return
	 */
	@SneakyThrows
	private Map<String, Boolean> lettucePipeline(List<String> users){
		LettuceConnection lettuceConnection = (LettuceConnection) stringRedisTemplate.getConnectionFactory().getConnection();
		// lettuceConnection.openPipeline();

		RedisClusterAsyncCommands<byte[], byte[]> commands = lettuceConnection.getNativeConnection();

		// 如果想达到打包发送请求的效果（类似jedis-pipeline），需要设置`autoFlushCommands=false`
		// disable auto-flushing
		commands.setAutoFlushCommands(false);
		commands.setTimeout(Duration.ofMinutes(1));

		// perform a series of independent calls
		List<RedisFuture<Long>> futures = Lists.newArrayList();
		for (String user : users) {
			futures.add(commands.exists(user.getBytes()));
		}

		// 因为`autoFlushCommands=false`，所以需要手动提交命令
		// write all commands to the transport layer
		commands.flushCommands();

		Map<String, Boolean> result = Maps.newHashMapWithExpectedSize(futures.size());

		for (int i = 0; i < futures.size(); i++) {
			RedisFuture<Long> value = futures.get(i);
			result.put(users.get(i), value != null && value.get() != null && value.get() > 0);
		}

		System.out.printf("[vergilyn][lettuce-pipeline] >>>> users.size: %d, futures.size: %d \n", users.size(), futures.size());

		// later
		lettuceConnection.close();

		return result;
	}

	/**
	 * <p> 如果是 lettuce，那么通过wireshark可知，还是 one-by-one。
	 * <p> 如果是 jedis，以下写法其实能达到期望的 pipeline效果。
	 *
	 */
	private void incorrectPipeline(List<String> isBlacklist){
		// 避免 pipeline 阻塞其它命令，不宜一次性执行太多。
		List<List<String>> partitions = Lists.partition(isBlacklist, 50);

		Map<String, Boolean> resultMap = Maps.newHashMapWithExpectedSize(isBlacklist.size());
		for (List<String> part : partitions) {
			List<Object> objects = stringRedisTemplate.executePipelined(new RedisCallback<Boolean>() {
				@Override
				public Boolean doInRedis(RedisConnection connection) throws DataAccessException {
					for (String user : part) {
						connection.exists(user.getBytes());
					}
					return null;
				}
			});

			for (int i = 0; i < part.size(); i++) {
				resultMap.put(part.get(i), (Boolean) objects.get(i));
			}
		}

		System.out.println("resultMap.size >>>> " + resultMap.size());
		for (Map.Entry<String, Boolean> entry : resultMap.entrySet()) {
			System.out.printf("%s >>>> isBlacklist: %b \n", entry.getKey(), entry.getValue());
		}

	}

	/**
	 * 166 + 3, 大约需要 200ms 左右。<b>主要还是 connection 占用太多</b>
	 *
	 * <p> 备注，spring-data-redis 默认使用的是 lettuce，所以貌似只会占用 1个connection，但是 request&response 会是多次。
	 *
	 * @param isBlacklist
	 */
	private void oneByOne(List<String> isBlacklist){
		for (String user : isBlacklist) {
			Boolean isBlack = stringRedisTemplate.hasKey(user);
			System.out.printf("%s >>>> isBlacklist: %b \n", user, isBlack);
		}
	}

	private void monitorClientList(){
		ScheduledExecutorService threadPool = Executors.newScheduledThreadPool(1);

		threadPool.scheduleAtFixedRate(() -> {
			List<RedisClientInfo> clientList = stringRedisTemplate.getClientList();

			System.out.printf("[vergilyn] redis client connections: %d \n", clientList.size());
		}, 10, 10, TimeUnit.MILLISECONDS);
	}

	private String buildKey(int id){
		return String.format("blacklist:user:%06d", id);
	}

	private String buildUser(int id){
		return "username-" + id;
	}
}
