package com.vergilyn.examples.jedis.command;

import com.vergilyn.examples.commons.utils.RedisCommandUtils;
import com.vergilyn.examples.jedis.AbstractJedisTests;

import lombok.SneakyThrows;
import org.junit.jupiter.api.Test;
import redis.clients.jedis.Protocol;

public class RESPProtocolTests extends AbstractJedisTests {

	@SneakyThrows
	@Test
	public void command2RESP(){
		Protocol.Command command = Protocol.Command.INCR;
		String key = "jedis:resp";

		String RESP = RedisCommandUtils.toRESP(command, key);
		System.out.printf("command: `%s`, \nRESP-len: %d, \nRESP: `%s`",
							new String(command.getRaw()) + " " + key,
							RESP.length(),
							RESP);
	}
}
