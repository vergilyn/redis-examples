package com.vergilyn.examples.cache;

import java.util.Optional;
import java.util.function.ToLongBiFunction;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
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

import static com.vergilyn.examples.cache.CacheConstants.DEFAULT_INCR;

/**
 * 常量命名：[key | field | value]_[true | false]_[string | hash | list | set | zset]。
 * `[常量类型]_[是否需要格式化]_[缓存类型]`.
 * <p/>
 * <p>
 * 备注：
 * <ol>
 * <li>lua中，0判断为true；</li>
 * <li>lua中，索引下标从1开始</li>
 * <li>XX: 仅仅更新存在的成员，不添加新成员</li>
 * <li>NX: 不更新存在的成员。只添加新成员</li>
 * <li>CH: 修改返回值为发生变化的成员总数，原始是返回新添加成员的总数 (CH 是 changed 的意思)</li>
 * <li>INCR: 当ZADD指定这个选项时，成员的操作就等同ZINCRBY命令，对成员的分数进行递增操作</li>
 * <li>java.lang.ClassCastException: java.lang.Integer cannot be cast to java.lang.String 因为没指定序列化方式，所以ARGV参数需要自己转换成string</li>
 * </ol>
 *
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
    private static final String K_F_ZSET_VOTE_ITEM_COUNT_TIMESTAMP = "vote:item:timestamp";

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

    private static final String V_F_ZSET_VOTE_EXPIRED_TIMESTAMP = "expired-timestamp";

    @Autowired
    private StringRedisTemplate redisTemplate;
    @Autowired
    private ObjectMapper objectMapper;

    private String keyCount(Vote vote) {
        Assert.notNull(vote);
        return keyCount(vote.getId());
    }

    private String keyCount(Long voteId) {
        Assert.notNull(voteId);
        return String.format(K_T_ZSET_VOTE_ITEM, voteId);
    }

    private String keyLog() {
        return K_F_LIST_VOTE_ITEM_LOG;
    }

    private String keyTimestamp() {
        return K_F_ZSET_VOTE_ITEM_COUNT_TIMESTAMP;
    }

    private String memberCount(VoteItem item) {
        Assert.notNull(item);
        return memberCount(item.getId());
    }

    private String memberCount(Long itemId) {
        Assert.notNull(itemId);
        return itemId + "";
    }

    private String memberTimestamp(VoteItem item) {
        Assert.notNull(item);
        Assert.notNull(item.getId());
        Assert.notNull(item.getVoteId());
        return item.getVoteId() + CacheConstants.SEPARATOR_CHAR + item.getId();
    }

    /**
     * 创建/修改投票活动 时，维护redis的expire-time。
     */
    public void adjustCountExpire(Vote vote) {
        String key = keyCount(vote);
        long expire = expireTimestamp(vote);

        //  ARGV[1] = "expire-timestamp", ARGV[2] = timestamp
        String script =
            "for i, key in ipairs(KEYS) do "
          + " redis.call('zadd', key, ARGV[2], ARGV[1]); "
          + " redis.call('pexpireat', key, ARGV[2]); "
          + "end";
        redisTemplate.execute(new DefaultRedisScript<>(script),
                Lists.newArrayList(key),
                Lists.newArrayList(V_F_ZSET_VOTE_EXPIRED_TIMESTAMP, expire));
    }

    /**
     * 如果缓存中不存在key-member，则以<code>item#count</code>做为初始值。
     *
     * @param item
     * @param log
     * @return 投票后的票数
     */
    public long incrDefaultCount(VoteItem item, VoteLog log) {
        long currentTimeMillis = System.currentTimeMillis();

        String kc = keyCount(item.getVoteId());
        String mc = memberCount(item);
        String sc = item.getCount() + DEFAULT_INCR + "";

        String kt = keyTimestamp();
        String mt = memberTimestamp(item);
        String st = currentTimeMillis + "";

        String kl = keyLog();
        String vl = null;
        try {
            vl = objectMapper.writeValueAsString(log);
        } catch (JsonProcessingException e) {
            e.printStackTrace();
        }

        // KEYS[1] = kt, ARGV[1] = st, ARGV[2] = mt
        // KEYS[2] = kl, ARGV[3] = vl
        // KEYS[3] = kc, ARGV[4] = mc, ARGV[5] = sc, ARGV[6] = INCR
        String script =
            "redis.call('lpush', KEYS[2], ARGV[3]); "
          + "local rs = redis.call('zadd', KEYS[3], 'XX', 'INCR', ARGV[6], ARGV[4]); "
          + "if(not rs) then "
          + "  rs = redis.call('zadd', KEYS[3], 'NX', 'CH', ARGV[5], ARGV[4]) == 0 "
          + "     and redis.call('zincrby', KEYS[3], ARGV[6], ARGV[4])"
          + "     or ARGV[5];"
          + "end "
          + "return rs + 0;";  // +0: 转换成数字。否则redisTemplate可能返回null

        return Optional.ofNullable(redisTemplate.execute(new DefaultRedisScript<Long>(script, Long.class),
                Lists.newArrayList(kt, kl, kc),
                st, mt, vl, mc, sc, DEFAULT_INCR + "")).orElse(0L);
    }

    /**
     * 防止特殊情况，log 只会在投票成功后才会添加到redis，意味着：可能投票成功，但丢失log。
     * 2次都需要更新timestamp的原因：
     * <pr>
     * 假设执行时间点顺序是
     * timestamp          desc
     * 150000             执行了incrCount
     * 150005             定时任务同步，会清除timestamp
     * 150010             执行addCount，所以必须再次更新timestamp
     * </pr>
     * <p>
     * 关于`log`和投票，无论（lua中命令顺序）怎么写都无法保证强一致性。
     * 都可能存在 count >= log数量。或者 log数量 ＞= count。
     * <p>
     * 可以在投票时，维护`count`失效时间。
     * 在实际的某些场景中，可能这么维护比较方便。但并不建议这种方案（缺陷，修改活动到期时间后，投票项不一定会被投票）。
     *
     * @return -1: 投票错误
     */
    public long incrCount(VoteItem item, VoteLog log, ToLongBiFunction<VoteItem, VoteLog> initCountFunction) {
        long currentTimeMillis = System.currentTimeMillis();

        String kc = keyCount(item.getVoteId());
        String mc = memberCount(item);

        String kt = keyTimestamp();
        String mt = memberTimestamp(item);
        String st = currentTimeMillis + "";

        String kl = keyLog();
        String vl = null;
        try {
            vl = objectMapper.writeValueAsString(log);
        } catch (JsonProcessingException e) {
            e.printStackTrace();
        }

        // KEYS[1] = kt, ARGV[1] = st, ARGV[2] = mt
        // KEYS[2] = kc, ARGV[3] = incr, ARGV[4] = mc, ARGV[6] = ec
        // KEYS[3] = kl, ARGV[5] = vl
        String script =
            "redis.call('zadd', KEYS[1], ARGV[1], ARGV[2]); "
          + "local rs, a = -1, redis.call('zadd', KEYS[2], 'XX', 'INCR', ARGV[3], ARGV[4]); "
          + "if(a) then "
          + "  rs = a; "
          + "  redis.call('lpush', KEYS[3], ARGV[5]); "
          + "end "
          + "return rs + 0;";

        Long execute = redisTemplate.execute(new DefaultRedisScript<>(script, Long.class),
                Lists.newArrayList(kt, kc, kl),
                st, mt, DEFAULT_INCR + "", mc, vl);

        if (execute == null || execute == -1) {
            long dbc = initCountFunction.applyAsLong(item, log);

            // KEYS[1] = kt, ARGV[1] = st, ARGV[2] = mt
            // KEYS[2] = kc, ARGV[3] = mc, ARGV[4] = dbc, ARGV[5] = incr
            // KEYS[3] = kl, ARGV[6] = vl
            script =
                "redis.call('zadd', KEYS[1], ARGV[1], ARGV[2]); "
              + "redis.call('lpush', KEYS[3], ARGV[6]); "
              + "local rs, a = -1, redis.call('zadd', KEYS[2], 'NX', 'CH', ARGV[4] + ARGV[5], ARGV[3]); "
              + "if(a == 0) then "
              + "  rs = redis.call('zincrby', KEYS[2], ARGV[5], ARGV[3])"
              + "else "
              + "  rs = ARGV[4] + ARGV[5]"
              + "end "
              + "return rs + 0;";

            execute = redisTemplate.execute(new DefaultRedisScript<>(script, Long.class),
                    Lists.newArrayList(kt, kc, kl),
                    st, mt, mc, dbc + "", DEFAULT_INCR + "", vl);
        }

        return execute == null ? 0 : execute;
    }

    private long expireTimestamp(Vote vote) {
        return DateUtils.addDays(vote.getEndTime(), 10).getTime();
    }

}
