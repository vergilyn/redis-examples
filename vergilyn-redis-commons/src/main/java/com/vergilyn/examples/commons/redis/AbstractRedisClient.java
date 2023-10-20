package com.vergilyn.examples.commons.redis;

import org.apache.commons.pool2.impl.GenericObjectPoolConfig;
import redis.clients.jedis.JedisPoolConfig;

import java.util.concurrent.TimeUnit;

/**
 * @author vergilyn
 * @since 2021-04-30
 */

public abstract class AbstractRedisClient {

	public static final String DEFAULT_HOST = "127.0.0.1";
	public static final int DEFAULT_PORT = 56379;

	@SuppressWarnings("ALL")
	protected final GenericObjectPoolConfig poolConfig(){
		JedisPoolConfig poolConfig = new JedisPoolConfig();
		poolConfig.setMaxTotal(64);
		poolConfig.setMinIdle(8);
		poolConfig.setMaxIdle(16);

		// 从JedisPool获取和归还连接时，都检测一下连接的有效性，失效的连接会被清理掉。
		// 虽然会多出两次ping的开销，但是不一定会造成瓶颈（具体看真实场景）
		poolConfig.setTestOnBorrow(true);
		poolConfig.setTestOnReturn(true);

		// 开启空闲连接检测
		poolConfig.setTestWhileIdle(true);

		// JedisPool中连接的空闲时间阈值，当达到这个阈值时，空闲连接就会被移除。
		// Redis的默认值是30分钟
		poolConfig.setMinEvictableIdleTimeMillis(TimeUnit.MINUTES.toMillis(30));

		// 检测空闲连接的周期
		poolConfig.setTimeBetweenEvictionRunsMillis(TimeUnit.SECONDS.toMillis(30));

		// 每次检测时，取多少个连接进行检测。如果设置成-1，就表示检测所有链接。
		poolConfig.setNumTestsPerEvictionRun(-1);

		return poolConfig;
	}
}
