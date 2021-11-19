--- 发送新消息
--- 1. page/unread/latest 都限制最多N个 entries。 3者绑定(entries 数量和关系应该是一样的)。
---   主要通过 key-latest 来判断
--- 2. 超过 fixed-size 时，剔除earliest-sendtime的数据。
---   通过 ZSET 的 `ZPOPMIN key [count]`，同时 通过`HDEL`删除 page&unread_count。
---   （每次可以 `ZPOPMIN key 10`, 避免频繁剔除）
---
--- KEYS[1]: `PAGE`
--- KEYS[2]: `UNREAD_COUNT`
--- KEYS[3]: `LATEST_SENDTIME`
--- ARGV[1]: fixed-size
--- ARGV[2]: `PAGE`&`UNREAD_COUNT`&`LATEST_SENDTIME` FIELD
--- ARGV[3]: `PAGE` VALUE
--- ARGV[4]: `LATEST_SENDTIME` SCORE
--- ARGV[5]: expire second

--- 1. 先新增，再判断剔除多余数据
--- HSET key field value
redis.call("HSET", KEYS[1], ARGV[2], ARGV[3]);

--- 处理标记已读/一键全读 造成负数的情况 （`HINCRBY`返回：增值操作执行后的该字段的值。）
local curCt = redis.call("HINCRBY", KEYS[2], ARGV[2], 1)
if curCt <= 0 then
    redis.call("HSET", KEYS[2], ARGV[2], 1)
end

--- ZADD key [NX|XX] [CH] [INCR] score member [score member ...]
redis.call("ZADD", KEYS[3], ARGV[4], ARGV[2]);

--- EXPIRE key seconds
redis.call("EXPIRE", KEYS[1], ARGV[5]);
redis.call("EXPIRE", KEYS[2], ARGV[5]);
redis.call("EXPIRE", KEYS[3], ARGV[5]);

local size = redis.call("ZCARD", KEYS[3]);
local trim = size - tonumber(ARGV[1]);
--- 超过 fixed-size
if (trim > 0) then
    --- redis-5.0+ 可以用`ZPOPMIN key [count]`
    local rmEntries = redis.call("ZRANGEBYSCORE", KEYS[3], 0, "+INF", "LIMIT", "0", trim);
    --- 移除 PAGE & UNREAD_COUNT
    redis.call("HDEL", KEYS[1], unpack(rmEntries));
    redis.call("HDEL", KEYS[2], unpack(rmEntries));
    redis.call("ZREM", KEYS[3], unpack(rmEntries));

    return size - trim;
end

return size;
