--[[ 参考 alibaba-sentinel
  - class, `abstract class LeapArray(int totalBuckets, int totalTimeIntervalInMs )`
  - `com.alibaba.csp.sentinel.cluster.flow.statistic.limit.RequestLimiter`

`windowLengthInMs = totalTimeIntervalInMs / totalBuckets`(强制约定入参必须整除)
redis数据结构: ()
    map, field -> bucket-index [0..N], value -> times
    zset, member -> bucket-index [0..N], score -> times

clear-expired-entries
   map,
     `HDEL key field [field ...]`
         时间复杂度：O(N) N是被删除的字段数量。
   zset,
     `ZREM key member [member ...]`
         时间复杂度：O(M*log(N)) with N being the number of elements in the sorted set and M the number of elements to be removed.
     `ZREMRANGEBYLEX key min max`
         简介： 删除名称按字典由低到高排序成员之间所有成员。
         时间复杂度：O(log(N)+M) with N being the number of elements in the sorted set and M the number of elements removed by the operation.
         **备注**：有序集合中分数必须相同! 如果有序集合中的成员分数有不一致的,结果就不准。

    既然都是计算出 过期的bucket-index，  那么 还是推荐 `map` 结构。

如过完全参照 alibaba-sentinel 翻译成redis-lua
redis-map
    windowLengthInMs    intervalInMs / sampleCount。强制整除
    intervalInMs        入参
    intervalInSecond    intervalInMs / 1000.0
    sampleCount         入参：bucket-count

    bucket_N            windowStart,count
                    `windowStart = timeMillis - timeMillis % windowLengthInMs`

    `N = (timeMillis / windowLengthInMs) % sampleCount`, 通过`HGET KEY bucket_N` 获取 `old = windowStart,count`。
    计算 `windowStart`,
    `old == null` -> `HSET key bucket_N windowStart,1`
    `old == windowStart` -> （递增）`HSET key bucket_N windowStart,count+1`
    `windowStart > old`  -> （重置，该bucket是新的一轮）`HSET key bucket_N windowStart,1`
    `windowStart < old`  ->  不同服务器时间误差导致，  计算当前所有的`sum`

以上的方案 也需要依赖 `currentTimeMillis`，不如另外种方式 `redis-sliding-window.lua` 好理解。

考虑是否可以用 `TTL / PTTL`替换 bucket_N 的计算方式？
假设 5s内 允许访问 3次。 `N = PTTL / 1000`(秒， 比TTL 得到的更精确)

[0, 1): N = 4, (如果 key 不存在，则 N = (expire - 1))
    第1次访问，`hincry bucket_4 1`。
    且 `expired 5`
[1, 2): N = 3
[2, 3): N = 2
[3, 4): N = 1,
    第2次访问，  获取`1 <= N <= 4(expired - 1)`, 计算 总次数 expendCount。
    expendCount > limitCount  限制， 不做别的操作！
    否则 未触发限制。 `hincry bucket_1 1` 同时 需要 `expired 5`

    第3次访问 如果第2次未触发限制，会重新`expired 5` 此时 `N = 4`
        无法区分这次`N=4`和 已有的`N=4`的关系。
[4, 5): N = 0


调整方案：`expire key 2*5`

[0, 1): N = 9, (如果 key 不存在，则 N = (2 * expire - 1s))
    第1次访问，`hincry bucket_9 1`。
    且 `expire key 2*5s`
[1, 2): N = 8
[2, 3): N = 7
[3, 4): N = 6,
    第2次访问，  获取`6 <= N < 6 + expired`, 计算 总次数 expendCount。
    expendCount > limitCount  限制， 不做别的操作！
    否则 未触发限制。 `hincry bucket_6 1`
        若 `N > expired` 没有后续操作
        若 `N <= expired` 重置`expire key 2*5s`

    第3次访问， （第二次没有 reset-expired）获取`6 <= N < 6 + expired`, 计算 总次数 expendCount。
        `hincry bucket_6 1`

[4, 5): N = 5
    `5 <= N <= 5 + expired`，此时 `expendCount >= 3`
    返回

[5, 6): N = 4
    此时要获取 `4 <= N < 4 + expired` (第1次的令牌释放)
         若 `N <= expired` 重置`expire key 2*5s`

[7, 8): N = 9 (因为上面 reset-expire)
）
]]--