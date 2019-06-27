package com.vergilyn.examples.cache;

import com.alibaba.fastjson.JSON;
import com.google.common.collect.Lists;
import com.vergilyn.examples.entity.Vote;
import com.vergilyn.examples.entity.VoteItem;
import com.vergilyn.examples.entity.VoteLog;

import org.apache.commons.lang3.time.DateUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.DefaultRedisScript;
import org.springframework.stereotype.Component;
import org.springframework.util.Assert;

/**
 * 常量命令：[key | field | member]_[true | false]_[string | hash | list | set | zset]。
 * `[常量类型]_[是否需要格式化]_[缓存类型]`.
 * <p/>
 *
 * 备注：
 * <ol>
 *   <li>lua中，0判断为true；</li>
 *   <li>lua中，索引下标从1开始</li>
 *   <li>XX: 仅仅更新存在的成员，不添加新成员</li>
 *   <li>NX: 不更新存在的成员。只添加新成员</li>
 *   <li>CH: 修改返回值为发生变化的成员总数，原始是返回新添加成员的总数 (CH 是 changed 的意思)</li>
 *   <li>INCR: 当ZADD指定这个选项时，成员的操作就等同ZINCRBY命令，对成员的分数进行递增操作</li>
 * </ol>
 * @author VergiLyn
 * @date 2019-06-26
 */
@Component
public class VoteCache {

    /**
     * <pre>
     * desc：保存某个投票活动的投票项的得票数
     * type：sort-set
     * key：vote:item:{vote.id}
     * member：{item.id}
     * score：{item.count}
     * expire：需要手动设置失效时间点，并且如果`vote.endTime`改变，需要关联修改。
     * </pre>
     */
    private static final String K_T_ZSET_VOTE_ITEM = "vote:item:%d";

    /**
     * <pre>
     * desc：保存投票项 最后一次得票时间（即增量更新的判断标志）
     * type：sort set
     * key：vote:item:count:timestamp
     * member：{vote.id}_{item.id}，需要解析出vote.id和item.id。
     * score：时间戳ms
     * expire：可以不自动失效
     * </pre>
     */
    private static final String K_F_ZSET_VOTE_ITEM_COUNT_TIMESTAMP = "vote:item:count:timestamp";

    /**
     * <pre>
     * desc：保存投票记录
     * type：list
     * key：vote:item:log
     * field：voteLog
     * expire：不自动失效，同步后从list中移除
     * </pre>
     */
    private static final String K_F_LIST_VOTE_ITEM_LOG = "vote:item:log";

    private static final String M_F_ZSET_VOTE_EXPIRED_TIMESTAMP = "expired-timestamp";

    private static final int INCR = 1;

    @Autowired
    private StringRedisTemplate redisTemplate;

    private String keyCount(Vote vote){
        Assert.notNull(vote);
        return keyCount(vote.getId());
    }

    private String keyCount(Long voteId){
        Assert.notNull(voteId);
        return String.format(K_T_ZSET_VOTE_ITEM, voteId);
    }

    private String keyLog(){
        return K_F_LIST_VOTE_ITEM_LOG;
    }

    private String keyTimestamp(){
        return K_F_ZSET_VOTE_ITEM_COUNT_TIMESTAMP;
    }

    private String memberCount(VoteItem item){
        Assert.notNull(item);
        return memberCount(item.getId());
    }

    private String memberCount(Long itemId){
        Assert.notNull(itemId);
        return itemId + "";
    }

    private String memberTimestamp(VoteItem item) {
        Assert.notNull(item);
        Assert.notNull(item.getId());
        Assert.notNull(item.getVoteId());
        return item.getVoteId() + "_" + item.getId();
    }

    public void adjustCountExpire(Vote vote){
        String key = keyCount(vote);
        long expire = DateUtils.addDays(vote.getEntTime(), 10).getTime();

        // （暂用）lua不一定是很好的减少redis连接的方式。
        //  ARGV[1] = "expire-timestamp", ARGV[2] = timestamp
        String script =
              "for i, key in ipairs(KEYS) do "
            + " redis.call('zadd', key, ARGV[2], ARGV[1]); "
            + " redis.call('pexpireat', key, ARGV[2]); "
            + "end";
        redisTemplate.execute(new DefaultRedisScript<>(script), Lists.newArrayList(key), Lists.newArrayList(M_F_ZSET_VOTE_EXPIRED_TIMESTAMP, expire));
    }

    /**
     * 如果缓存中不存在key-member，则以<code>item#count</code>做为起始值。
     * @param item
     * @param log
     * @return 投票后的票数
     */
    public Long incrDefaultCount(VoteItem item, VoteLog log){
        long currentTimeMillis = System.currentTimeMillis();

        String keyCount = keyCount(item.getVoteId());
        String memberCount = memberCount(item);
        int newCount = item.getCount() + INCR;

        String keyTimestamp = keyTimestamp();
        String memberTimestamp = memberTimestamp(item);
        long scoreTimestamp = currentTimeMillis;

        String keyLog = keyLog();
        String memberLog = JSON.toJSONString(log);

        // key[1] = keyCount, key[2] = keyTimestamp, key[3] = keyLog
        // ARGV[1] = newCount, ARGV[2] = incr, ARGV[3] = memberCount
        // ARGV[4] = scoreTimestamp, ARGV[5] = memberTimestamp
        // ARGV[6] = memberLog
        String script =
              "redis.call('zadd', KEYS[2], ARGV[4], ARGV[5]); "
            + "redis.call('lpush', KEYS[3], ARGV[6]); "
            + "local rs = redis.call('zadd', 'XX', 'INCR', ARGV[2], ARGV[3]); "
            + "if(rs) "
            + "then "
            + "  return rs; "
            + "else "
                + "local ch = redis.call('zadd', KEYS[1], 'NX', 'CH', ARGV[1], ARGV[3]); "
                + "if(0 == ch) "
                + "then "
                + "  return redis.call('zincrby', KEYS[1], ARGV[2], ARGV[3]); "
                + "else "
                + "  return redis.call('zscore', KEYS[1], ARGV[3]); "
                + "end "
            + "end ";

        return redisTemplate.execute(new DefaultRedisScript<>(script, Long.class), Lists.newArrayList(keyCount, keyTimestamp, keyLog),
                newCount, INCR, memberCount,
                scoreTimestamp, memberTimestamp,
                memberLog);
    }

    /**
     * @param item
     * @param log
     * @return -1: key-member不存在
     */
    public Long incrCount(VoteItem item, VoteLog log){
            long currentTimeMillis = System.currentTimeMillis();

            String keyCount = keyCount(item.getVoteId());
            String memberCount = memberCount(item);

            String keyTimestamp = keyTimestamp();
            String memberTimestamp = memberTimestamp(item);
            long scoreTimestamp = currentTimeMillis;

            String keyLog = keyLog();
            String valueLog = JSON.toJSONString(log);

            // key[1] = keyCount, key[2] = keyTimestamp, key[3] = keyLog
            // ARGV[1] = incr, ARGV[2] = memberCount
            // ARGV[3] = scoreTimestamp, ARGV[4] = memberTimestamp
            // ARGV[5] = valueLog
            // lua 中：0 也是 true；索引下标从1开始
            String script =
                  "redis.call('zadd', KEYS[2], ARGV[3], ARGV[4]); "
                + "redis.call('lpush', KEYS[3], ARGV[5]); "
                + "local rs = redis.call('zadd', KEYS[1], 'XX', 'INCR', ARGV[1], ARGV[2]); "
                + "if(rs) "
                + "then "
                + "  return rs; "
                + "else "
                + "  return -1; "
                + "end ";

            // 因为没指定序列化方式，所以ARGV参数需要自己转换成string。
            // java.lang.ClassCastException: java.lang.Integer cannot be cast to java.lang.String
            return redisTemplate.execute(new DefaultRedisScript<>(script, Long.class), Lists.newArrayList(keyCount, keyTimestamp, keyLog),
                    INCR + "", memberCount,
                    scoreTimestamp + "", memberTimestamp,
                    valueLog);
    }

    /**
     * 因为会先调用{@linkplain #incrCount(VoteItem, VoteLog)}， 其中已保存{@linkplain VoteLog}。
     * 但是特别注意，需要更新timestamp。原因：
     * <pr>
     *   假设执行时间点顺序是
     *   timestamp          desc
     *   150000             执行了incrCount
     *   150005             定时任务同步
     *   150010             执行addCount
     *
     *   所以，需要再次更新timestamp
     * </pr>
     * @param item
     * @return
     */
    public Long addCount(VoteItem item, int initCount){
        long currentTimeMillis = System.currentTimeMillis();

        String keyTimestamp = keyTimestamp();
        String memberTimestamp = memberTimestamp(item);
        long scoreTimestamp = currentTimeMillis;

        String keyCount = keyCount(item.getVoteId());
        String memberCount = memberCount(item);
        int newCount = initCount + INCR;

        // KEYS[1] = keyTimestamp, KEYS[2] = keyCount
        // ARGV[1] = scoreTimestamp, ARGV[2] = memberTimestamp
        // ARGV[3] = newCount, ARGV[4] = memberCount, ARGV[5] = INCR
        String script =
              "redis.call('zadd', KEYS[1], ARGV[1], ARGV[2]); "
            + "local ch = redis.call('zadd', KEYS[2], 'NX', 'CH', ARGV[3], ARGV[4]); "
            + "if(0 == ch) "
            + "then "
            + "  return redis.call('zincrby', KEYS[2], ARGV[5], ARGV[4]); "
            + "else "
            + "  return redis.call('zscore', KEYS[2], ARGV[4]); "
            + "end ";

        return redisTemplate.execute(new DefaultRedisScript<>(script, Long.class), Lists.newArrayList(keyTimestamp, keyCount),
                scoreTimestamp, memberTimestamp,
                newCount, memberCount, INCR);
    }
}
