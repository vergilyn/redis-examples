-- KEYS[1]:
-- ARGV[1]: 当前时间戳，毫秒。由于redis-lua拒绝随机写，所以可以在代码中调用`redis.call("TIME")`。
-- ARGV[2]：时间，单位秒。（也是key的失效时间）
-- ARGV[3]: 限流次数
-- ARGV[4]: 集合最大元素数量 （必须超过 ARGV[1]，默认是 `2 * ARGV[3]`）
-- ARGV[5]: 随机数，避免 `zset.member` 重复。
--
-- FIXME 2022-04-26，
--   1) 只能精确到 毫秒，且需要保证 唯一! (具体看传入的 `ARGV[1]` 的精度， 相应的要调整 `intervalInMs`的计算规则)
--   2) 此redis-lua依赖传入的 `currTimestampMillis`，如果服务器之间时间差过大，会导致此滑动窗口限制错误！
--   3) 占用了过多的redis内存空间。特别是当“限流次数”越大时
--

local currTimestampMillis = tonumber(ARGV[1]);
local intervalInSecond = tonumber(ARGV[2]);
local intervalInMs = intervalInSecond * 1000;
local maxCount = tonumber(ARGV[3]);
local maxZsetEntries = tonumber(ARGV[4]);
local randomStr = ARGV[5];
-- JAVA代码中判断处理，不要依赖redis-lua中判断处理
--if (maxZsetEntries < maxCount) then
--    maxZsetEntries = 2 * maxCount;
--end

-- 防止服务器时间差，统一用redis的时间
-- `TIME`: 返回内容包含两个元素，1) UNIX时间戳（单位：秒）2) 微秒
-- local times = redis.call("TIME");
-- local currTimestampMillis = times[1] * 1000000 + times[2];

-- XXX 2022-04-26 限制了 currTimestampMillis 和 intervalInSecond 对应的单位！
local minScore = currTimestampMillis - intervalInMs;
-- `ZCOUNT key min max`: 指定分数范围的元素个数。 (默认包括score值等于min或max)的成员
local validCount = redis.call("ZCOUNT", KEYS[1], minScore, "+INF");

local latelyUnlock;
if (validCount < maxCount) then
    -- ZADD key [NX|XX] [CH] [INCR] score member [score member ...]
    -- `ERROR: Write commands not allowed after non deterministic commands`
    --   原因：redis-lua基于数据一致性考虑，要求脚本必须是纯函数的形式，也就是说对于一段Lua脚本给定相同的参数，写入Redis的数据也必须是相同的，对于随机性的写入Redis是拒绝的。
    --   所以：无法在 redis-lua 中调用 `redis.call("TIME")`，将此 time写入某个key！
    --
    -- 如果 `member = currTimestampMillis`需要保证其唯一，否则会造成错误！！！（或者传入一个 随机数或者唯一标识，避免此情况！例如 `member = currTimestampMillis,Random[0, 10000...]`）
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
-- redis.call("EXPIRE", KEYS[1], intervalInSecond);

-- 返回值：
--   具体的member值：触发限制，返回“最近的一个解除限制的 member”
--   -1：未触发限制
return latelyUnlock;
