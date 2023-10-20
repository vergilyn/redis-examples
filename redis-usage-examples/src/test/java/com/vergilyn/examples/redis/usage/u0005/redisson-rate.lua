-- 例如：集群下，每间隔 30MINUTES 允许请求 5次。
-- Java代码示例：
--   RRateLimiter rateLimiter = redissonClient.getRateLimiter("vergilyn:20221208");
--   rateLimiter.setRate(RateType.OVERALL, 5, 30, RateIntervalUnit.MINUTES);
--   rateLimiter.expire(Duration.of(60, ChronoUnit.MINUTES));
--   boolean b = rateLimiter.tryAcquire(1);
--
-- 1) ① 和 ② 都可以算作是 初始化。 之后每次获取限流令牌时 不需要多次调用 ②。（只需要 ①）
-- 2) 每次获取限流令牌时，都需要调用 ③ 和 ④。
--    如果不每次调用 ③，当key-expired时，限流令牌池会被重置，导致滑动限流令牌不正确。
--    （如果不调用 ③，会导致相关的key永久占用redis）

-- ************************************************************** --
-- 参考 redisson: org.redisson.RedissonRateLimiter#setRateAsync(RateType type, long rate, long rateInterval, RateIntervalUnit unit)
--  0: RateType.OVERALL（集群）         Total rate for all RateLimiter instances.
--  1: RateType.PER_CLIENT（单机）      Total rate for all RateLimiter instances working with the same Redisson instance
--
-- KEYS[1]: "vergilyn:20221208"
-- KEYS[2]: "{vergilyn:20221208}:value"
-- KEYS[3]: "{vergilyn:20221208}:permits"
--
-- ARGV[1]: 5         <=> rate
-- ARGV[2]: 1800000   <=> rateInterval 转成 毫秒
-- ARGV[3]: 0         <=> RateType.OVERALL.ordinal()
redis.call('hset', KEYS[1], 'rate', ARGV[1]);
redis.call('hset', KEYS[1], 'interval', ARGV[2]);
redis.call('hset', KEYS[1], 'type', ARGV[3]);
redis.call('del', KEYS[2], KEYS[3]);


-- ************************************************************** --
-- 参考：org.redisson.RedissonExpirable.expireAsync(long, java.util.concurrent.TimeUnit, java.lang.String, java.lang.String...)
--
-- KEYS[1]: "vergilyn:20221208"
-- KEYS[2]: "{vergilyn:20221208}:value"
-- KEYS[3]: "{vergilyn:20221208}:value:b6902619-4a73-4b85-8a37-38c8be53d1a0"
-- KEYS[4]: "{vergilyn:20221208}:permits"
-- KEYS[5]: "{vergilyn:20221208}:permits:b6902619-4a73-4b85-8a37-38c8be53d1a0"
--
-- ARGV[1]: 600000      <=> key失效时间，毫秒
-- ARGV[2]: "NX"        <=> 参考 `PEXPIRE key milliseconds [NX | XX | GT | LT]`
--
-- `pexpire`: 设置成功，返回 1；key 不存在或设置失败，返回 0。
local result = 0;
for j = 1, #KEYS, 1 do
    local expireSet;
    if ARGV[2] ~= '' then
        expireSet = redis.call('pexpire', KEYS[j], ARGV[1], ARGV[2]);
    else
        expireSet = redis.call('pexpire', KEYS[j], ARGV[1]);
    end;
    if expireSet == 1 then
        result = expireSet;
    end;
end;
return result;

-- ************************************************************** --
-- Hash     vergilyn:20221208
--      rate        5
--      interval    1800000
--      type        0
-- String   {vergilyn:20221208}:value
--      0  （指代剩余的 permits，会重新计算，所以就算`0` 也不代表不能获取到令牌）
-- Zset     {vergilyn:20221208}:permits
--      1670498292958   \x08\x1b\xd2\x0d\xedK\xc5fZ\x01\x00\x00\x00
--      ....

-- ************************************************************** --
-- 参考：org.redisson.RedissonRateLimiter.tryAcquireAsync(org.redisson.client.protocol.RedisCommand<T>, java.lang.Long)
-- 例如 `rateLimiter.tryAcquire(1)`
--
-- KEYS[1]: "vergilyn:20221208"
-- KEYS[2]: "{vergilyn:20221208}:value"
-- KEYS[3]: "{vergilyn:20221208}:value:3f4f8c1f-a2c6-4e0e-a4be-4dd5db5d6484"
-- KEYS[4]: "{vergilyn:20221208}:permits"
-- KEYS[5]: "{vergilyn:20221208}:permits:3f4f8c1f-a2c6-4e0e-a4be-4dd5db5d6484"
--
-- KEYS[2] & KEYS[4] 是 集群 使用的 key
-- KEYS[3] & KEYS[5] 是 单机 使用的，后面的 `3f4f8c1f-a2c6-4e0e-a4be-4dd5db5d6484` 指 redisson-client-id
--
-- ARGV[1]: 1 <=> `tryAcquire(1)`中的参数
-- ARGV[2]: System.currentTimeMillis()
-- ARGV[3]: random <=> 随机字符串  （zset 的 member）

local rate = redis.call('hget', KEYS[1], 'rate');
local interval = redis.call('hget', KEYS[1], 'interval');
local type = redis.call('hget', KEYS[1], 'type');
assert(rate ~= false and interval ~= false and type ~= false, 'RateLimiter is not initialized')

-- `type == '1'` 单机模式，  默认 集群模式
local valueName = KEYS[2];
local permitsName = KEYS[4];
if type == '1' then
    valueName = KEYS[3];
    permitsName = KEYS[5];
end;

assert(tonumber(rate) >= tonumber(ARGV[1]), 'Requested permits amount could not exceed defined rate');

local currentValue = redis.call('get', valueName);
local res;
if currentValue ~= false then
    local expiredValues = redis.call('zrangebyscore', permitsName, 0, tonumber(ARGV[2]) - interval);
    local released = 0;
    for i, v in ipairs(expiredValues) do
        local random, permits = struct.unpack('Bc0I', v);
        released = released + permits;
    end;

    if released > 0 then
        redis.call('zremrangebyscore', permitsName, 0, tonumber(ARGV[2]) - interval);
        if tonumber(currentValue) + released > tonumber(rate) then
            currentValue = tonumber(rate) - redis.call('zcard', permitsName);
        else
            currentValue = tonumber(currentValue) + released;
        end;
        redis.call('set', valueName, currentValue);
    end;

    if tonumber(currentValue) < tonumber(ARGV[1]) then
        local firstValue = redis.call('zrange', permitsName, 0, 0, 'withscores');
        -- 个人猜测：  假设限流规则是  每 10秒 允许 3次。
        --      15
        --      13		3
        --      11		2
        --      10		1       第一次，（System.currentTimeMillis = 10）
        --
        -- 3 + 10 - (15-10) = 3 + 10 - 5 = 8
        -- 这个 `3`应该 只是为了保证 xx毫秒之后，一定会有1个 有效的令牌（不考虑并发，）
        res = 3 + interval - (tonumber(ARGV[2]) - tonumber(firstValue[2]));
    else
        redis.call('zadd', permitsName, ARGV[2], struct.pack('Bc0I', string.len(ARGV[3]), ARGV[3], ARGV[1]));
        redis.call('decrby', valueName, ARGV[1]);
        res = nil;
    end;
else
    redis.call('set', valueName, rate);
    redis.call('zadd', permitsName, ARGV[2], struct.pack('Bc0I', string.len(ARGV[3]), ARGV[3], ARGV[1]));
    redis.call('decrby', valueName, ARGV[1]);
    res = nil;
end;

local ttl = redis.call('pttl', KEYS[1]);
if ttl > 0 then
    redis.call('pexpire', valueName, ttl);
    redis.call('pexpire', permitsName, ttl);
end;

-- res = nil, 表示获取到令牌，即可以通过
-- res = xx, 表示 xx毫秒后 一定会释放1个被占用的令牌。  例如 程序中可以 sleepMs(xx) 然后再重新获取令牌
return res;