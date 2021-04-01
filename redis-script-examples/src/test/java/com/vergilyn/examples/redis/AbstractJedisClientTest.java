package com.vergilyn.examples.redis;

import java.io.BufferedReader;
import java.io.FileInputStream;
import java.io.InputStreamReader;

import lombok.SneakyThrows;
import org.apache.commons.lang3.StringUtils;
import org.apache.commons.pool2.impl.GenericObjectPoolConfig;
import redis.clients.jedis.Jedis;
import redis.clients.jedis.JedisPool;

import static java.nio.charset.StandardCharsets.UTF_8;

public abstract class AbstractJedisClientTest {

	protected static final String HOST = "127.0.0.1";
	protected static final int PORT = 56379;

	public static final String LUA_NOTE_PREFIX = "--";

	protected final JedisPool _jedisPool;

	public AbstractJedisClientTest() {
		GenericObjectPoolConfig poolConfig = new GenericObjectPoolConfig();
		poolConfig.setMaxTotal(64);
		poolConfig.setMinIdle(0);
		poolConfig.setMaxIdle(8);

		this._jedisPool = new JedisPool(poolConfig, HOST, PORT);
	}

	/**
	 * 使用完记得释放连接 {@linkplain Jedis#close()}
	 */
	protected Jedis jedis(){
		return _jedisPool.getResource();
	}

	@SneakyThrows
	protected String getScript(String scriptName){
		ClassLoader classLoader = this.getClass().getClassLoader();
		String filepath = classLoader.getResource("").toURI().getPath() + scriptName;

		try (FileInputStream input = new FileInputStream(filepath)){
			InputStreamReader reader = new InputStreamReader(input, UTF_8);
			BufferedReader bufferedReader = new BufferedReader(reader);

			String line;
			StringBuilder out = new StringBuilder();
			while (true){
				line = bufferedReader.readLine();
				if (line == null){
					break;
				}


				if (line.trim().startsWith(LUA_NOTE_PREFIX) || StringUtils.isBlank(line)){
					continue;
				}

				out.append(line).append('\n');
			}

			return out.toString();
		}
	}
}
