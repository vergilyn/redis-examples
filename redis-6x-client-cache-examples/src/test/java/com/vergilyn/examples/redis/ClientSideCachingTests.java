package com.vergilyn.examples.redis;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

import io.lettuce.core.RedisClient;
import io.lettuce.core.RedisURI;
import io.lettuce.core.TrackingArgs;
import io.lettuce.core.api.StatefulRedisConnection;
import io.lettuce.core.support.caching.CacheAccessor;
import io.lettuce.core.support.caching.CacheFrontend;
import io.lettuce.core.support.caching.ClientSideCaching;
import lombok.SneakyThrows;
import org.junit.jupiter.api.Test;

/**
 *
 * @author vergilyn
 * @since 2021-08-27
 */
public class ClientSideCachingTests extends AbstractRedisLettuceClientTests {
	private static final String KEY = "client-side-caching";

	/**
	 * TODO 2021-08-27
	 * 1. 调整demo
	 * 2. 前缀匹配 {@linkplain TrackingArgs#prefixes(String...)}
	 * 3. lettuce 实现代码阅读：主要是怎么接受 redis-server推送的变更
	 */
	@SneakyThrows
	@Test
	public void test() {
		// <1> 创建单机连接的连接信息
		RedisURI redisUri = RedisURI.builder()                    //
				.withHost("127.0.0.1")
				.withPort(16379)
				.build();
		RedisClient redisClient = RedisClient.create(redisUri);

		StatefulRedisConnection<String, String> connection = redisClient.connect();
		/**
		 * FIXME 2021-08-27 貌似不能使用 pool代理的connection
		 * ERROR: io.lettuce.core.support.$Proxy14 cannot be cast to io.lettuce.core.StatefulRedisConnectionImpl
		 *
		 * {@link ClientSideCaching#create(CacheAccessor, StatefulRedisConnection)} 此处是强转换。
		 */
		connection = statefulRedisConnection();

		// <2> 创建缓存访问器
		Map<String, String> clientCache = new ConcurrentHashMap<>(); //map 自动保存所有操作key的 key=value
		CacheFrontend<String, String> frontend = ClientSideCaching.enable(CacheAccessor.forMap(clientCache), connection,
				TrackingArgs.Builder.enabled());

		// <3> 客户端正常写入测试数据 k1 v1
		syncRedisCommand().set(KEY, "v1");

		// <4> 循环读取
		while (true) {
			// <4.1> 缓存访问器中的值，查看是否和 Redis 服务端同步
			String cachedValue = frontend.get(KEY);
			System.out.println("当前 k1 的值为:--->" + cachedValue);
			Thread.sleep(3000);
		}
	}
}
