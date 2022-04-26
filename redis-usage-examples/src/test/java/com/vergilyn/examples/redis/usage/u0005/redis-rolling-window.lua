-- KEYS[1]:
-- ARGV[1]: 当前时间戳，毫秒。由于redis-lua拒绝随机写，所以可以在代码中调用`redis.call("TIME")`。
-- ARGV[2]：时间，单位秒。（也是key的失效时间）
-- ARGV[3]: 限流次数
-- ARGV[4]: 集合最大元素数量 （必须超过 ARGV[1]，默认是 `2 * ARGV[1]`）

local currTimestamp = tonumber(ARGV[1]);
local limitSeconds = tonumber(ARGV[2]);
local limitCount = tonumber(ARGV[3]);
local maxMemberCount = tonumber(ARGV[4]);
if (maxMemberCount < limitCount) then
    maxMemberCount = 2 * limitCount;
end

-- 防止服务器时间差，统一用redis的时间
-- `TIME`: 返回内容包含两个元素，1) UNIX时间戳（单位：秒）2) 微秒
-- local times = redis.call("TIME");
-- local currTimestamp = times[1] * 1000000 + times[2];

-- XXX 2022-04-26 限制了 currTimestamp 和 limitSeconds 对应的单位！
local minScore = currTimestamp - (limitSeconds * 1000);
-- `ZCOUNT key min max`: 指定分数范围的元素个数。
local validCount = redis.call("ZCOUNT", KEYS[1], minScore, "+INF");

local isLimit = true;
if (validCount < limitCount) then
    -- ZADD key [NX|XX] [CH] [INCR] score member [score member ...]
    -- `ERROR: Write commands not allowed after non deterministic commands`
    --   原因：redis-lua基于数据一致性考虑，要求脚本必须是纯函数的形式，也就是说对于一段Lua脚本给定相同的参数，写入Redis的数据也必须是相同的，对于随机性的写入Redis是拒绝的。
    --   所以：无法在 redis-lua 中调用 `redis.call("TIME")`，将此 time写入某个key！
    redis.call("ZADD", KEYS[1], currTimestamp, currTimestamp);
    isLimit = false;
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
-- 取舍，避免`ZCOUNT`或每次都直接`ZREMRANGEBYSCORE`性能过低，所以通过`ZCARD`获取zset大小，如果超过`maxMemberCount`才调用`ZREMRANGEBYSCORE`
local total = redis.call("ZCARD", KEYS[1]);
if total >= maxMemberCount then
    redis.call("ZREMRANGEBYSCORE", KEYS[1], "-INF", "(" .. minScore);
end

-- 设置key失效时间
redis.call("EXPIRE", KEYS[1], limitSeconds);

return isLimit;
