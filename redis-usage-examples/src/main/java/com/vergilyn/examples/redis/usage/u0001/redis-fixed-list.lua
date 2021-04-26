--- 固定大小的 List
--- KEYS[1]: redis-key
--- ARGV[1]: List fixed-size
--- ARGV[2+]: lpush args

--- lpush integer-reply: 在 push 操作后的 list 长度。
local size = redis.call("lpush", KEYS[1], unpack(ARGV, 2));
local trim = tonumber(ARGV[1]) - size;
if (trim <= -1) then
    redis.call("ltrim", KEYS[1], 0, trim - 1);
end
--- 也可以通过前面计算出来，避免调用一次`llen`
return redis.call("llen", KEYS[1]);