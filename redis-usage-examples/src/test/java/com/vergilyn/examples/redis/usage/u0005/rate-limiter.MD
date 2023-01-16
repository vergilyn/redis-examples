# rate limiter

## Redisson RateLimiter

- Redisson RateLimiter: [https://github.com/redisson/redisson/blob/redisson-3.18.1/redisson/src/main/java/org/redisson/RedissonRateLimiter.java](https://github.com/redisson/redisson/blob/redisson-3.18.1/redisson/src/main/java/org/redisson/RedissonRateLimiter.java)

```java
RedissonClient redissonClient = RedissonClientFactory.createRedissonClient();

RRateLimiter rateLimiter = redissonClient.getRateLimiter("vergilyn:20221208");

// 每3秒，允许2次
rateLimiter.setRate(RateType.OVERALL, 5, 30, RateIntervalUnit.MINUTES);


```
