package com.vergilyn.examples.redis;

import java.util.Properties;
import java.util.concurrent.Semaphore;
import java.util.concurrent.TimeUnit;

import javax.annotation.Resource;

import com.google.common.collect.Lists;
import com.vergilyn.examples.config.RedisConfiguration;

import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.ImportAutoConfiguration;
import org.springframework.boot.test.autoconfigure.data.redis.DataRedisTest;
import org.springframework.context.ApplicationContext;
import org.springframework.context.annotation.AnnotationConfigApplicationContext;
import org.springframework.data.redis.connection.MessageListener;
import org.springframework.data.redis.connection.RedisConnection;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.listener.KeyExpirationEventMessageListener;
import org.springframework.data.redis.listener.KeyspaceEventMessageListener;
import org.springframework.data.redis.listener.RedisMessageListenerContainer;
import org.springframework.data.redis.listener.Topic;

/**
 * @author vergilyn
 * @since 2021-04-30
 */
@Slf4j
@DataRedisTest
@ImportAutoConfiguration(RedisConfiguration.class)
public abstract class AbstractRedisClientTests {
	@Autowired
	private ApplicationContext applicationContext;
	@Resource
	protected StringRedisTemplate stringRedisTemplate;
	@Resource
	protected RedisMessageListenerContainer redisMessageListenerContainer;

	protected final RedisKyesapceListener redisKyesapceListener = new RedisKyesapceListener();

	protected <T> T registerAndGetBean(Class<T> clazz){
		final AnnotationConfigApplicationContext context = annotationConfigApplicationContext();
		context.registerBean(clazz);

		return context.getBean(clazz);
	}

	protected AnnotationConfigApplicationContext annotationConfigApplicationContext(){
		return (AnnotationConfigApplicationContext) applicationContext;
	}

	/**
	 *
	 * @param timeout "<= 0" prevent exit.
	 * @param unit timeout unit
	 */
	protected void awaitExit(long timeout, TimeUnit unit){
		try {
			final Semaphore semaphore = new Semaphore(0);
			if (timeout > 0){
				semaphore.tryAcquire(timeout, unit);
			}else {
				semaphore.acquire();
			}
		} catch (InterruptedException e) {
		}
	}

	/**
	 * <pre>
	 *   #  K     Keyspace events, published with __keyspace@<db>__ prefix.
	 *   #  E     Keyevent events, published with __keyevent@<db>__ prefix.
	 *   #  g     Generic commands (non-type specific) like DEL, EXPIRE, RENAME, ...
	 *   #  $     String commands
	 *   #  l     List commands
	 *   #  s     Set commands
	 *   #  h     Hash commands
	 *   #  z     Sorted set commands
	 *   #  x     Expired events (events generated every time a key expires)
	 *   #  e     Evicted events (events generated when a key is evicted for maxmemory)
	 *   #  A     Alias for g$lshzxe, so that the "AKE" string means all the events.
	 * </pre>
	 *
	 * 当注册监听{@linkplain KeyExpirationEventMessageListener} or {@linkplain KeyspaceEventMessageListener}时，其内部会判断
	 * 当前redis `notify-keyspace-events`是否为empty，如果empty则`config set notify-keyspace-events EA`。
	 * SEE: {@linkplain KeyspaceEventMessageListener#init()}
	 *
	 */
	protected class RedisKyesapceListener {
		public void registerRedisKeyExpirationEventMessageListener(){
			enableKeyspaceEvent("AEx");
			registerAndGetBean(KeyExpirationEventMessageListener.class);
		}

		public void registerRedisListener(MessageListener listener, Topic topic){
			redisMessageListenerContainer.addMessageListener(listener, Lists.newArrayList(topic));
		}

		/**
		 *
		 * @see KeyspaceEventMessageListener#init()
		 */
		private void enableKeyspaceEvent(String keyspaceNotificationsConfigParameter){
			RedisConnection connection = redisMessageListenerContainer.getConnectionFactory().getConnection();
			try {
				String configKey = "notify-keyspace-events";
				connection.setConfig(configKey, keyspaceNotificationsConfigParameter);

				Properties config = connection.getConfig(configKey);

				log.warn("redis command >>>> 'config set {}', expected: {}, actual: {}",
						configKey, keyspaceNotificationsConfigParameter, config.getProperty(configKey));

			}finally {
				connection.close();
			}
		}
	}
}
