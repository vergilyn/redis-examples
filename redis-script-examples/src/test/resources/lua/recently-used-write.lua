--- "最近使用"，例如 最近浏览商品，最近聊天
--- KEYS[1]:
--- ARGV[1]: fixed-size
--- ARGV[2]: expired (s)
--- ARGV[3]: score
--- ARGV[4+]: members

--- 减少传输大小，所以在lua中组装[score member...]
local score_members = {};
for key,value in ipairs(ARGV)
do
    if (key >= 4) then
        table.insert(score_members, ARGV[3]);
        table.insert(score_members, value)
    end
end

--- ZADD key [NX|XX] [CH] [INCR] score member [score member ...]
redis.call("ZADD", KEYS[1], unpack(score_members));

local fixed_size = tonumber(ARGV[1]);
--- O(log(N)): with N being the number of elements in the sorted set.
-- local count = redis.call("ZCOUNT", KEYS[1], "-inf", "+inf");
--- O(1): 返回key的有序集元素个数。
local count = redis.call("ZCARD", KEYS[1]);
if (count > fixed_size) then
    --- 除有序集key中，指定排名(rank)区间内的所有成员。0: 分数最小的元素；-1: 分数最高的元素
    redis.call("ZREMRANGEBYRANK", KEYS[1], 0, count - fixed_size - 1);
end
return redis.call("EXPIRE", KEYS[1], ARGV[2]);