package com.vergilyn.examples.listener;

import org.springframework.beans.factory.annotation.Autowired;
import redis.clients.jedis.Jedis;
import redis.clients.jedis.JedisPubSub;

/**
 *
 * @author VergiLyn
 * @date 2019-05-23
 */
//@Component
public class JedisExpiredListener extends JedisPubSub {

    public final static String LISTENER_PATTERN = "__keyevent@*__:expired";

    /**
     * 虽然能注入,但貌似在listener-class中jedis无法使用(无法建立连接到redis),exception message:
     * "only (P)SUBSCRIBE / (P)UNSUBSCRIBE / QUIT allowed in this context"
     */
    @Autowired
    private Jedis jedis;

    /**
     * 初始化按表达式的方式订阅时候的处理
     * @param pattern
     * @param subscribedChannels
     */
    @Override
    public void onPSubscribe(String pattern, int subscribedChannels) {
        System.out.print("onPSubscribe >> ");
        System.out.println(String.format("pattern: %s, subscribedChannels: %d", pattern, subscribedChannels));
    }

    /**
     * 取得按表达式的方式订阅的消息后的处理
     * @param pattern
     * @param channel
     * @param message
     */
    @Override
    public void onPMessage(String pattern, String channel, String message) {
        System.out.print("onPMessage >> ");
        System.out.println(String.format("key: %s, pattern: %s, channel: %s", message, pattern, channel));
    }

    /**
     * 取得订阅的消息后的处理
     * @param channel
     * @param message
     */
    @Override
    public void onMessage(String channel, String message) {
        super.onMessage(channel, message);
    }

    /**
     * 初始化订阅时候的处理
     * @param channel
     * @param subscribedChannels
     */
    @Override
    public void onSubscribe(String channel, int subscribedChannels) {
        super.onSubscribe(channel, subscribedChannels);
    }

    /**
     * 取消订阅时候的处理
     * @param channel
     * @param subscribedChannels
     */
    @Override
    public void onUnsubscribe(String channel, int subscribedChannels) {
        super.onUnsubscribe(channel, subscribedChannels);
    }

    /**
     * 取消按表达式的方式订阅时候的处理
     * @param pattern
     * @param subscribedChannels
     */
    @Override
    public void onPUnsubscribe(String pattern, int subscribedChannels) {
        super.onPUnsubscribe(pattern, subscribedChannels);
    }
}
