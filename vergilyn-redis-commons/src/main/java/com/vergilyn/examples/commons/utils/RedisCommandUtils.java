package com.vergilyn.examples.commons.utils;

import java.io.ByteArrayOutputStream;

import io.lettuce.core.protocol.BaseRedisCommandBuilder;
import io.lettuce.core.protocol.Command;
import io.lettuce.core.protocol.CommandType;
import org.apache.commons.text.StringEscapeUtils;
import redis.clients.jedis.Protocol;
import redis.clients.jedis.util.RedisOutputStream;

import static java.nio.charset.StandardCharsets.UTF_8;

/**
 * @author vergilyn
 * @since 2021-05-07
 */
@SuppressWarnings("JavadocReference")
public abstract class RedisCommandUtils {

	/**
	 * jedis
	 */
	public static String toRESP(Protocol.Command command, String key, String... args){
		try (ByteArrayOutputStream os = new ByteArrayOutputStream();
			RedisOutputStream redisOutputStream = new RedisOutputStream(os, 8192)){

			Protocol.sendCommand(redisOutputStream, command, key.getBytes(UTF_8));
			redisOutputStream.flush();

			return StringEscapeUtils.escapeJava(os.toString());
		}catch (Exception e){
			e.printStackTrace();
		}

		return "";
	}

	/**
	 * spring-data-redis lettuce
	 *
	 * @see Command#encode(io.netty.buffer.ByteBuf)
	 * @see BaseRedisCommandBuilder
	 * @see io.lettuce.core.RedisCommandBuilder
	 */
	public static String toRESP(CommandType command, String key, String... args){
		// VTODO 2021-05-07
		throw new UnsupportedOperationException("todo 2021-05-07");
	}
}
