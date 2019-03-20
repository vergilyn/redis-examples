# spring-boot-data-redis

（以下内容待整理！）

## TODO
1. 整理以下内容

2. `RedisTemplate<K, V>`的 RedisSerializer: 
- Jackson2JsonRedisSerializer
- FastJsonRedisSerializer
- StringRedisSerializer

## spring-boot集成redis

- [springboot之使用RedisTemplate优雅地操作redis](https://www.cnblogs.com/superfj/p/9232482.html)
- [SpringBoot中注入RedisTemplate实例异常解决](https://blog.csdn.net/zhaoheng314/article/details/81564166)
- [RedisTemplate SerializationFailedException: Failed to deserialize payload 异常解决](https://www.cnblogs.com/shihaiming/p/6019795.html)

默认的StringRedisTemplate只能得到String，需要单独的转换结果。

RedisTemplate<K, V>支持自动序列化，但是V必须实现java.io.Serializable。
并且，key/value均不可读。
**待解决：redisTemplate达到想要的效果！**

2. spring-boot中jedis与lettuce
- [Redis的三个框架：Jedis,Redisson,Lettuce](https://www.cnblogs.com/liyan492/p/9858548.html)
- [spring boot 集成 redis lettuce](https://www.cnblogs.com/taiyonghai/p/9454764.html)
- [有关lettuce连接池的疑问](https://segmentfault.com/q/1010000015866837)

jedis：当多线程使用同一个连接时，是线程不安全的。所以要使用连接池，为每个jedis实例分配一个连接。
Lettuce：当多线程使用同一连接实例时，是线程安全的。

spring-boot默认1.x使用jedis，2.x使用lettuce，若需要替换参考：
[howto-use-jedis-instead-of-lettuce](https://docs.spring.io/spring-boot/docs/2.1.2.RELEASE/reference/htmlsingle/#howto-use-jedis-instead-of-lettuce)

```
<dependency>
	<groupId>org.springframework.boot</groupId>
	<artifactId>spring-boot-starter-data-redis</artifactId>
	<exclusions>
		<exclusion>
			<groupId>io.lettuce</groupId>
			<artifactId>lettuce-core</artifactId>
		</exclusion>
	</exclusions>
</dependency>
<dependency>
	<groupId>redis.clients</groupId>
	<artifactId>jedis</artifactId>
</dependency>
```

并且lettuce的pool依赖apache-commons-pool2（貌似jedis-pool也依赖），所以需要引入依赖（spring-boot-start-data-redis-2.1.2.RELEASE中未依赖commons-pool2）
```
<dependency>
    <groupId>org.apache.commons</groupId>
    <artifactId>commons-pool2</artifactId>
</dependency>
```


## REDIS [RedisProperties](https://github.com/spring-projects/spring-boot/blob/v2.1.2.RELEASE/spring-boot-project/spring-boot-autoconfigure/src/main/java/org/springframework/boot/autoconfigure/data/redis/RedisProperties.java)
```
spring.redis.cluster.max-redirects= # Maximum number of redirects to follow when executing commands across the cluster.
spring.redis.cluster.nodes= # Comma-separated list of "host:port" pairs to bootstrap from.
spring.redis.database=0 # Database index used by the connection factory.
spring.redis.url= # Connection URL. Overrides host, port, and password. User is ignored. Example: redis://user:password@example.com:6379
spring.redis.host=localhost # Redis server host.
spring.redis.jedis.pool.max-active=8 # Maximum number of connections that can be allocated by the pool at a given time. Use a negative value for no limit.
spring.redis.jedis.pool.max-idle=8 # Maximum number of "idle" connections in the pool. Use a negative value to indicate an unlimited number of idle connections.
spring.redis.jedis.pool.max-wait=-1ms # Maximum amount of time a connection allocation should block before throwing an exception when the pool is exhausted. Use a negative value to block indefinitely.
spring.redis.jedis.pool.min-idle=0 # Target for the minimum number of idle connections to maintain in the pool. This setting only has an effect if it is positive.
spring.redis.lettuce.pool.max-active=8 # Maximum number of connections that can be allocated by the pool at a given time. Use a negative value for no limit.
spring.redis.lettuce.pool.max-idle=8 # Maximum number of "idle" connections in the pool. Use a negative value to indicate an unlimited number of idle connections.
spring.redis.lettuce.pool.max-wait=-1ms # Maximum amount of time a connection allocation should block before throwing an exception when the pool is exhausted. Use a negative value to block indefinitely.
spring.redis.lettuce.pool.min-idle=0 # Target for the minimum number of idle connections to maintain in the pool. This setting only has an effect if it is positive.
spring.redis.lettuce.shutdown-timeout=100ms # Shutdown timeout.
spring.redis.password= # Login password of the redis server.
spring.redis.port=6379 # Redis server port.
spring.redis.sentinel.master= # Name of the Redis server.
spring.redis.sentinel.nodes= # Comma-separated list of "host:port" pairs.
spring.redis.ssl=false # Whether to enable SSL support.
spring.redis.timeout= # Connection timeout.
```

```
# jedisPoolConfig 参考

<bean id="jedisPoolConfig" class="redis.clients.jedis.JedisPoolConfig">
   <property name="maxTotal" value="8" /><!-- 最大连接数, 默认8个 -->
   <property name="maxIdle" value="8" /><!-- 最大空闲连接数, 默认8个 -->
   <property name="maxWaitMillis" value="-1" /><!-- 获取连接时的最大等待毫秒数(如果设置为阻塞时BlockWhenExhausted),如果超时就抛异常, 小于零:阻塞不确定的时间,  默认-1 -->
   <property name="numTestsPerEvictionRun" value="3" /><!-- 每次逐出检查时 逐出的最大数目 如果为负数就是 : 1/abs(n), 默认3 -->
   <property name="minEvictableIdleTimeMillis" value="-1" /><!-- 逐出连接的最小空闲时间 默认60000毫秒(1分钟) -->
   <property name="timeBetweenEvictionRunsMillis" value="30000" /><!-- 逐出扫描的时间间隔(毫秒) 如果为负数,则不运行逐出线程, 默认-1 -->
   <property name="softMinEvictableIdleTimeMillis" value="10000" /><!--  -->
   <property name="testOnBorrow" value="true" /><!-- 在获取连接的时候检查有效性, 默认false -->
   <property name="testWhileIdle" value="true" /><!-- 在空闲时检查有效性, 默认false -->
   <property name="testOnReturn" value="false" /><!--  -->
   <property name="blockWhenExhausted" value="false" /><!-- 连接耗尽时是否阻塞, false报异常,ture阻塞直到超时, 默认true -->
</bean>
```













