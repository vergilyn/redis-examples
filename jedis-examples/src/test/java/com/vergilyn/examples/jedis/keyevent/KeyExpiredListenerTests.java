package com.vergilyn.examples.jedis.keyevent;

import java.time.LocalTime;
import java.util.List;

import com.alibaba.fastjson.JSON;
import com.vergilyn.examples.jedis.AbstractJedisTests;

import lombok.SneakyThrows;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import redis.clients.jedis.Jedis;
import redis.clients.jedis.JedisPubSub;

/**
 * 需求场景：
 *   从redis定期写数据到DB，例如将“浏览量”保存到redis（每次浏览+1也只是incr redis），
 * 定期将redis保存的“浏览量”写回到DB，为了尽可能保证正确，期望当key-expired时能触发一次回写DB。
 *
 * <p>
 *   key过期事件推送的{@linkplain JedisPubSub#onPMessage(String, String, String) onPMessage(pattern, channel, message)}
 *   {@code message}只有key（无value），所以无法满足需求场景。
 *
 * </p>
 *
 * <p>
 *  当是key-expired-event时，redis服务在key失效时(或失效后)通知到java服务某个key失效了，
 *  那么在java中不可能得到这个redis-key对应的redis-value。
 *
 *  <pre>
 *    <b>解决方案:</b>
 *      创建copy/shadow key, 例如`set vkey "vergilyn"`; 对应copykey: `set copykey:vkey "any-value" ex 10`;
 *      真正的key是"vkey"(业务中使用), 失效触发key是"copykey:vkey"(其value为空字符为了减少内存空间消耗，或任意字符串，没有实际用途)。
 *      当"copykey:vkey"触发失效时, 从"vkey"得到失效时的值, 并在逻辑处理完后"del vkey"。
 *  </pre>
 *  <pre>
 *    <b>缺陷:</b>
 *    1: 存在多余的key; (copykey/shadowkey)
 *    2: 不严谨, 假设copykey在 12:00:00失效, 通知在12:10:00收到, 这间隔的10min内程序修改了key, 得到的并不是 失效时的value.
 *
 *    (第1点影响不大; 第2点貌似redis本身的Pub/Sub就不是严谨的, 失效后还存在value的修改, 应该在设计/逻辑上杜绝)
 *  </pre>
 * </p>
 *
 * @author vergilyn
 * @since 2021-05-25
 */

class KeyExpiredListenerTests extends AbstractJedisTests {

	static final int KEY_EXPIRED = 2;
	static final String CONFIG_KEYSPACE_EVENTS = "notify-keyspace-events";

	/**
	 * <pre>
	 *   参考redis目录下redis.conf中的"EVENT NOTIFICATION", redis默认的db{0, 15}一共16个数据库
	 *      K    Keyspace events, published with __keyspace@<db>__ prefix.
	 *      E    Keyevent events, published with __keyevent@<db>__ prefix.
	 *      x    Expired events (events generated every time a key expires)
	 * </pre>
	 */
	static final String CONFIG_KEYSPACE_EVENTS_VALUE = "KEx";

	@BeforeEach
	public void beforeEach(){
		final Jedis jedis = jedis();

		jedis.configSet(CONFIG_KEYSPACE_EVENTS, CONFIG_KEYSPACE_EVENTS_VALUE);

		jedis.setex("key-expired-listener", KEY_EXPIRED, LocalTime.now().toString());

	}

	@SneakyThrows
	@Test
	public void listener(){
		final Jedis jedis = jedis();
		final List<String> configGet = jedis.configGet(CONFIG_KEYSPACE_EVENTS);

		System.out.println("[vergilyn] redis.conf >>>> " + JSON.toJSONString(configGet));

		/* psubscribe是一个阻塞的方法(内部do-while)，在取消订阅该频道前，会一直阻塞在这，只有当取消了订阅才会执行下面的other code；
		 * 可以onMessage/onPMessage里面收到消息后，调用了unsubscribe()/onPUnsubscribe(); 来取消订阅，这样才会执行后面的other code
		 */
		jedis.psubscribe(new KeyExpiredListener(), KeyExpiredListener.LISTENER_PATTERN);

		// other code
	}
}
