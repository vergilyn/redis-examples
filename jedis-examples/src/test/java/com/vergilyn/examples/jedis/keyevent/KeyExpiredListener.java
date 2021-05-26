package com.vergilyn.examples.jedis.keyevent;

import java.time.LocalTime;

import com.vergilyn.examples.commons.redis.JedisClientFactory;

import redis.clients.jedis.Jedis;
import redis.clients.jedis.JedisPubSub;

class KeyExpiredListener extends JedisPubSub {
	/**
	 * 实际通知的channel 例如"__keyevent@0__:expired"
	 */
	public static final String LISTENER_PATTERN = "__keyevent@*__:expired";

	private static void printf(String format, Object... args){
		System.out.printf("[%s][vergilyn] #%s(...) >>>> ",
				LocalTime.now(), Thread.currentThread().getStackTrace()[2].getMethodName());
		System.out.printf(format, args);
		System.out.println();
	}

	@Override
	public void onPSubscribe(String pattern, int subscribedChannels) {
		printf("pattern: %s, subscribedChannels: %d", pattern, subscribedChannels);

		super.onPSubscribe(pattern, subscribedChannels);
	}

	// message 只包含`key`，并不存在`value`。并且由于的key-expired-event，此时value已经不存在，无法acquire.
	@Override
	public void onPMessage(String pattern, String channel, String message) {
		printf("pattern: %s, channel: %s, message: %s", pattern, channel, message);

		Jedis jedis = JedisClientFactory.getInstance().jedis();
		String value = jedis.get(message);
		printf("command `get %s` value: %s", message, value);

		super.onPMessage(pattern, channel, message);
	}

	@Override
	public void onMessage(String channel, String message) {
		printf("channel: %s, message: %s", channel, message);

		super.onMessage(channel, message);
	}

	@Override
	public void onSubscribe(String channel, int subscribedChannels) {
		printf("channel: %s, subscribedChannels: %d", channel, subscribedChannels);

		super.onSubscribe(channel, subscribedChannels);
	}

	@Override
	public void onUnsubscribe(String channel, int subscribedChannels) {
		printf("channel: %s, subscribedChannels: %d", channel, subscribedChannels);

		super.onUnsubscribe(channel, subscribedChannels);
	}

	@Override
	public void onPUnsubscribe(String pattern, int subscribedChannels) {
		printf("pattern: %s, subscribedChannels: %d", pattern, subscribedChannels);

		super.onPUnsubscribe(pattern, subscribedChannels);
	}
}
