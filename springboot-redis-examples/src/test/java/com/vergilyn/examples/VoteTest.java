package com.vergilyn.examples;

import java.util.Date;

import com.alibaba.fastjson.JSON;
import com.vergilyn.examples.cache.VoteCache;
import com.vergilyn.examples.entity.Vote;
import com.vergilyn.examples.entity.VoteItem;
import com.vergilyn.examples.entity.VoteLog;

import org.apache.commons.lang3.time.DateUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.testng.annotations.Test;
import org.testng.collections.Lists;
import redis.clients.jedis.Jedis;

/**
 * @author VergiLyn
 * @date 2019-06-24
 */
public class VoteTest extends BasicTestng {
    @Autowired
    private VoteCache voteCache;

    private Vote vote;
    private VoteItem item;
    private VoteLog log;
    public static final String KEY_VOTE_LOG = "vote:item:log";
    public static final String KEY_VOTE_APP = "vote:item:%s:%s:vote_num";
    public static final String KEY_VOTE_WX = "vote:item:%s:%s:vote_num_wx";
    public static final String KEY_VOTE_TOTAL = "vote:item:%s:%s:vote_num_total";
    public static final String KEY_VOTE_TIMESTAMP= "vote:item:type:timestamp";

    @Test
    public void vote(){
        VoteItem item = new VoteItem();
        item.setId(1001L);
        item.setVoteId(1L);

        VoteLog voteLog = new VoteLog();
        voteLog.setVoteTime(new Date());

        System.out.println(voteCache.incrCount(item, voteLog));
    }

    @Test
    protected void init(){
        Date date = new Date();

        vote = new Vote();
        vote.setId(2L);
        vote.setEndTime(DateUtils.addHours(date, 1));

        item = new VoteItem();
        item.setId(1001L);
        item.setVoteId(vote.getId());
        item.setCount(1);
        item.setType("分类");

        log = new VoteLog();
        log.setVoteTime(date);
        log.setUserId(-1L);
        log.setVoteId(item.getVoteId());
        log.setVoteItemId(item.getId());
    }

    @Test(dependsOnMethods = "init")
    public void incrVote(){
        int incr = 1;
        String incrStr = incr + "";
        String flag = "app";

        String kApp = String.format(KEY_VOTE_APP, item.getVoteId(), item.getType());
        String mNum = item.getId() + "";

        String kWx = String.format(KEY_VOTE_WX, item.getVoteId(), item.getType());

        String kTotal = String.format(KEY_VOTE_TOTAL, item.getVoteId(), item.getType());

        String kLog = KEY_VOTE_LOG;
        String kTs = KEY_VOTE_TIMESTAMP;

        String vLog = JSON.toJSONString(log);

        String mTs = item.getVoteId() + "_" + item.getType() + "_" + item.getId();
        String sTs = System.currentTimeMillis() + "";

        String expire = DateUtils.addHours(vote.getEndTime(), 2).getTime() + "";

        // KEYS[1] = kApp, KEYS[2] = kWx, KEYS[3] = kLog, KEYS[4] = kTs, KEYS[5] = kTotal
        // ARGV[1] = flag, ARGV[2] = mNum, ARGV[3] = incrStr
        // ARGV[4] = vLog
        // ARGV[5] = mTs, ARGV[6] = sTs
        // ARGV[7] = expire
        String script =
              "local rs = ARGV[1] == 'app' and"
                      + " redis.call('zadd', KEYS[1], 'XX', 'INCR', ARGV[3], ARGV[2]) "
                      + " or redis.call('zadd', KEYS[2], 'XX', 'INCR', ARGV[3], ARGV[2]); "
            + "if(rs) "
            + "then "
            + "  redis.call('lpush', KEYS[3], ARGV[4]); "
            + "  redis.call('zadd', KEYS[4], ARGV[6], ARGV[5]); "
            + "  rs = redis.call('zadd', KEYS[5], 'XX', 'INCR', ARGV[3], ARGV[2]);"
            + "  redis.call('pexpireat', KEYS[1], ARGV[7]); "
            + "  redis.call('pexpireat', KEYS[2], ARGV[7]); "
            + "  redis.call('pexpireat', KEYS[5], ARGV[7]); "
            + "  return rs + 0;"  // +0: 转换成数字
            + "else "
            + " return -1; "
            + "end";

        System.out.println(Lists.newArrayList(kApp, kWx, kLog, kTs, kTotal,
                flag, mNum, incrStr, vLog, mTs, sTs, expire));

        Jedis jedis = jedisPool.getResource();
        Long eval = (Long) jedis.eval(script, 5, kApp, kWx, kLog, kTs, kTotal,
                    flag, mNum, incrStr, vLog, mTs, sTs, expire);

        System.out.println("if >>>> " + eval);

        if (eval <= 0){
            int app = 10, wx = 2;
            String appNum = app + ("app".equals(flag) ? incr : 0) + "";
            String wxNum = wx + ("app".equals(flag) ? 0 : incr) + "";
            String totalNum = app + wx + incr + "";

            // KEYS[1] = kApp, KEYS[2] = kWx, KEYS[3] = kTotal, KEYS[4] = kLog, KEYS[5] = kTs
            // ARGV[1] = mNum, ARGV[2] = appNum, ARGV[3] = wxNum, ARGV[4] = totalNum
            // ARGV[5] = flag, ARGV[6] = vLog, ARGV[7] = mTs, ARGV[8] = sTs
            // ARGV[9] = expire
            script =
                  "redis.call('lpush', KEYS[4], ARGV[6]); "
                + "redis.call('zadd', KEYS[5], ARGV[8], ARGV[7]); "
                + "local rs, a = -1, redis.call('zadd', KEYS[1], 'NX', ARGV[2], ARGV[1]); "
                + "if(a) "
                + "then "
                + "  redis.call('zadd', KEYS[2], 'NX', ARGV[3], ARGV[1]); "
                + "  redis.call('zadd', KEYS[3], 'NX', ARGV[4], ARGV[1]); "
                + "  rs = ARGV[4]; "
                + "else "
                + "  redis.call('zincrby', ARGV[5] == 'app' and KEYS[1] or KEYS[2], ARGV[1]); "
                + "  rs = redis.call('zincrby', KEYS[3], ARGV[1]); "
                + "end "
                + "redis.call('pexpireat', KEYS[1], ARGV[9]); "
                + "redis.call('pexpireat', KEYS[2], ARGV[9]); "
                + "redis.call('pexpireat', KEYS[3], ARGV[9]); "
                + "return rs + 0; ";

            eval = (Long) jedis.eval(script, 5, kApp, kWx, kTotal, kLog, kTs,
                    mNum, appNum, wxNum, totalNum, flag, vLog, mTs, sTs, expire);

            System.out.println("else >>>> " + eval);
        }

        jedis.close();
    }
}
