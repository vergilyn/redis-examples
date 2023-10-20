-- KEYS[1]:
-- ARGV[1]: 当前时间戳，毫秒。由于redis-lua拒绝随机写，所以可以在代码中调用`redis.call("TIME")`。
-- ARGV[2]：时间，单位秒。（也是key的失效时间）
-- ARGV[3]: 限流次数
-- ARGV[4]: 集合最大元素数量 （必须超过 ARGV[1]，默认是 `2 * ARGV[3]`）
-- ARGV[5]: 随机数，避免 `zset.member` 重复。
--
-- 2023-01-17，在`redis-sliding-window.lua`的基础上，参考`redisson-rate.lua`进行调整。
--
-- 备注：
--   1) 减少内存占用：不要直接保存距离`1970-01-01`的毫秒时间戳。可以将其换成例如距离 `2023-01-01 00:00:00.000` 的毫秒差。

local currTimestampMillis = tonumber(ARGV[1]);
local intervalInSecond = tonumber(ARGV[2]);
local intervalInMs = intervalInSecond * 1000;
local maxCount = tonumber(ARGV[3]);
local maxZsetEntries = tonumber(ARGV[4]);
local randomStr = ARGV[5];

-- FIXME 2023-01-17 限制了 currTimestampMillis 和 intervalInSecond 对应的单位！
--   redis的key失效时间 最小时间单位是 毫秒，所以可以强制约束使用“毫秒”作为单位。
local minScore = currTimestampMillis - intervalInMs;
-- `ZCOUNT key min max`: 指定分数范围的元素个数。 (默认包括score值等于min或max)的成员
local validCount = redis.call("ZCOUNT", KEYS[1], minScore, "+INF");

local latelyUnlock;
if (validCount < maxCount) then
    redis.call("ZADD", KEYS[1], currTimestampMillis, currTimestampMillis .. randomStr);
    latelyUnlock = -1;
else
    -- 最近一个解锁的member。必须是`[minScore, +INF]`，不能是 `[minScore, currTimestampMillis]`（当前时间戳 currTimestampMillis 比最小score都还小时，会得到nil）
    -- ZRANGEBYSCORE key min max [WITHSCORES] [LIMIT offset count]
    latelyUnlock = redis.call("ZRANGEBYSCORE", KEYS[1], minScore, "+INF", "WITHSCORES", "LIMIT", "0", "1")[2];
    if latelyUnlock == nil then
        -- 避免业务端多余判，返回最大值
        latelyUnlock = currTimestampMillis + intervalInMs;
    else
        latelyUnlock = latelyUnlock + intervalInMs;
    end
end

-- 删除失效的members。未做任何判断，会导致始终有一次`ZREMRANGEBYSCORE`
-- ZREMRANGEBYSCORE key min max: 通过给参数前增加`(`符号来使用可选的开区间(小于或大于)
--   时间复杂度：O(log(N)+M) with N being the number of elements in the sorted set and M the number of elements removed by the operation.
--
-- ZCOUNT: 时间复杂度：O(log(N)) with N being the number of elements in the sorted set.
-- local expiredCount = redis.call("ZCOUNT", KEYS[1], "-INF", "(" .. minScore);
--
-- ZCARD: 时间复杂度：O(1)
--
-- 取舍，避免`ZCOUNT`或每次都直接`ZREMRANGEBYSCORE`性能过低，所以通过`ZCARD`获取zset大小，如果超过`maxZsetEntries`才调用`ZREMRANGEBYSCORE`
local total = redis.call("ZCARD", KEYS[1]);
if total >= maxZsetEntries then
    redis.call("ZREMRANGEBYSCORE", KEYS[1], "-INF", "(" .. minScore);
end

-- 设置key失效时间
-- FIXME 每次获取令牌都设置失效时间是否会过于拖慢性能？
redis.call("EXPIRE", KEYS[1], intervalInSecond);

-- 返回值：
--   具体的member值：触发限制，返回“最近的一个解除限制的 member”
--   -1：未触发限制
return latelyUnlock;
