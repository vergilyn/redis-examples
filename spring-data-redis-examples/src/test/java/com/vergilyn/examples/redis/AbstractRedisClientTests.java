package com.vergilyn.examples.redis;

import com.google.common.collect.Lists;
import com.vergilyn.examples.redis.autoconfigred.SliceTestRedisAutoConfiguration;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.ImportAutoConfiguration;
import org.springframework.boot.test.autoconfigure.data.redis.DataRedisTest;
import org.springframework.context.ApplicationContext;
import org.springframework.context.annotation.AnnotationConfigApplicationContext;
import org.springframework.core.env.Environment;
import org.springframework.data.redis.connection.MessageListener;
import org.springframework.data.redis.connection.RedisConnection;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.listener.KeyExpirationEventMessageListener;
import org.springframework.data.redis.listener.KeyspaceEventMessageListener;
import org.springframework.data.redis.listener.RedisMessageListenerContainer;
import org.springframework.data.redis.listener.Topic;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.ContextConfiguration;

import javax.annotation.Resource;
import java.util.Properties;
import java.util.concurrent.Semaphore;
import java.util.concurrent.TimeUnit;

/**
 * TODO 2021-05-27 无法理解`@DataRedisTest`到底怎么用！感觉下面这样写就是`@SpringBootTest`
 *
 * @author vergilyn
 * @since 2021-04-30
 *
 * @see <a href="https://docs.spring.io/spring-boot/docs/2.2.11.RELEASE/reference/htmlsingle/#boot-features-testing-spring-boot-applications-testing-autoconfigured-redis-test">
 *          Auto-configured Data Redis Tests</a>
 */
@Slf4j
@DataRedisTest
@ActiveProfiles(profiles = {"redis"})
@ContextConfiguration(classes = SpringDataRedisApplication.class)
@ImportAutoConfiguration(SliceTestRedisAutoConfiguration.class)
public abstract class AbstractRedisClientTests {
	@Autowired
	private ApplicationContext applicationContext;
	@Autowired
	protected Environment environment;

	@Resource
	protected StringRedisTemplate stringRedisTemplate;
	@Resource
	protected RedisMessageListenerContainer redisMessageListenerContainer;

	protected final RedisKeyspaceListener redisKeyspaceListener = new RedisKeyspaceListener();

	protected <T> T registerAndGetBean(Class<T> clazz){
		AnnotationConfigApplicationContext context = annotationConfigApplicationContext();
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
	protected class RedisKeyspaceListener {
		private RedisKeyspaceListener() {
		}

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
