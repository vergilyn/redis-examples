package com.vergilyn.examples.commons.redis;

import io.lettuce.core.RedisClient;
import io.lettuce.core.RedisURI;
import io.lettuce.core.api.StatefulRedisConnection;
import io.lettuce.core.api.sync.RedisCommands;
import io.lettuce.core.resource.ClientResources;
import io.lettuce.core.support.ConnectionPoolSupport;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.pool2.impl.GenericObjectPool;

@Slf4j
public class LettuceRedisClientFactory extends AbstractRedisClient {
	private static LettuceRedisClientFactory instance;
	private GenericObjectPool<StatefulRedisConnection<String, String>> lettucePool;

	private LettuceRedisClientFactory(String host, int port) {
		this.lettucePool = this.poolRedisConnection(host, port);
	}

	public static LettuceRedisClientFactory getInstance() {
		return getInstance(DEFAULT_HOST, DEFAULT_PORT);
	}
	public static LettuceRedisClientFactory getInstance(String host, int port) {
		if (instance != null){
			return instance;
		}

		synchronized (RedisClientFactory.class) {
			if (instance != null) {
				return instance;
			}

			instance = new LettuceRedisClientFactory(host, port);
			return instance;
		}
	}

	/**
	 * FIXME 2021-08-27 暂时先这么简单的用来测试
	 * @see <a href="https://github.com/lettuce-io/lettuce-core/blob/6.1.0.RELEASE/src/test/java/io/lettuce/examples/ConnectToRedis.java">ConnectToRedis.java</a>
	 * @see StatefulRedisConnection#close()
	 */
	public RedisCommands syncRedisCommand() throws Exception {
		return statefulRedisConnection().sync();
	}

	public StatefulRedisConnection<String, String> statefulRedisConnection() throws Exception {
		return this.lettucePool.borrowObject();
	}

	/**
	 * @see <a href="https://github.com/lettuce-io/lettuce-core/wiki/Connection-Pooling">Connection-Pooling</a>
	 */
	protected GenericObjectPool<StatefulRedisConnection<String, String>> poolRedisConnection(String host, int port) {
		try {
			return ConnectionPoolSupport.createGenericObjectPool(() -> redisClient(host, port).connect(), poolConfig());
		}catch (Exception e){
			log.error("", e);
			return null;
		}
	}

	protected RedisClient redisClient(String host, int port) {
		return RedisClient.create(buildClientResources(), buildRedisURI(host, port));
	}

	protected RedisURI buildRedisURI(String host, int port) {
		return RedisURI.Builder.redis(host, port).build();
	}

	protected ClientResources buildClientResources() {
		final ClientResources.Builder builder = ClientResources.builder();

		// redis-6x client-side-caching
		// builder.tracing(BraveTracing.builder().build());

		return builder.build();
	}
}
