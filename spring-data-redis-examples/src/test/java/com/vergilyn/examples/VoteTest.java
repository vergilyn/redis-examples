package com.vergilyn.examples;

import java.util.Date;
import java.util.List;

import com.alibaba.fastjson.JSON;
import com.alibaba.fastjson.TypeReference;
import com.vergilyn.examples.cache.VoteCache;
import com.vergilyn.examples.entity.Vote;
import com.vergilyn.examples.entity.VoteItem;
import com.vergilyn.examples.entity.VoteLog;
import com.vergilyn.examples.service.VoteService;

import org.apache.commons.lang3.time.DateUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.testng.annotations.Test;
import redis.clients.jedis.Jedis;

/**
 * @author VergiLyn
 * @date 2019-06-24
 */
public class VoteTest extends AbstractTestng {
    @Autowired
    private VoteService voteService;
    @Autowired
    private VoteCache voteCache;

    private Vote vote;
    private VoteItem item;
    private VoteLog log;


    @Test
    protected void init(){
        Date date = new Date();

        vote = voteService.saveOrUpdate(1L, "投票活动", date, DateUtils.addHours(date, 1));

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
    public void incrDefaultCount(){
        Long rs = voteCache.incrDefaultCount(item, log);
        System.out.println("incrDefaultCount >>>> " + rs);
    }

    @Test(dependsOnMethods = "init")
    public void incrVote(){
        long count = voteCache.incrCount(item, log, (item1, log1) -> 20L);

        System.out.println("incrVote >>>> initDB: " + count);
    }

    @Test
    public void zrerank(){
        Jedis jedis = jedisPool.getResource();

        Long zrevrank = jedis.zrevrank("zset", "2");
        System.out.println("zrevrank >>>> " + zrevrank);
    }

    @Test
    public void reset(){
        String script =
              "for i, k in ipairs(KEYS) "
            + "do "
            + "  for j, m in ipairs(redis.call('zrange', k, 0, -1))"  // 注意数量，避免阻塞
            + "  do "
            + "    redis.call('zadd', k, 10, m)"
            + "  end "
            + "end ";

        jedisPool.getResource().eval(script, 2, "z1", "z2");
    }

    @Test
    public void batch() {
        String json = "[\"vote:item:1229:分类1:vote_num\",\"vote:item:1229:分类1:vote_num_wx\",\"vote:item:1229:分类1:vote_num_total\",\"vote:item:1229:分类2:vote_num\",\"vote:item:1229:分类2:vote_num_wx\",\"vote:item:1229:分类2:vote_num_total\",\"vote:item:1229:分类2:vote_num\",\"vote:item:1229:分类2:vote_num_wx\",\"vote:item:1229:分类2:vote_num_total\",\"2\",\"1\",\"3\",\"65961\",\"65959\",\"65960\"]";

        List<String> params = JSON.parseObject(json, new TypeReference<List<String>>() {});

        String script =
              "local j,p,s,e = 2, ARGV[1]+1, 0, ARGV[1]+1;"
            + "for i=1,#KEYS,3 do "
            + "  s = e + 1; "
            + "  e = p + ARGV[j];"
            + "  j = j + 1; "
            + "  redis.call('zrem', KEYS[i], unpack(ARGV,s,e)); "
            + "  redis.call('zrem', KEYS[i+1], unpack(ARGV,s,e)); "
            + "  redis.call('zrem', KEYS[i+2], unpack(ARGV,s,e)); "
            + "end ";

        jedisPool.getResource().eval(script, 12, params.toArray(new String[0]));
    }

    @Test
    public void multiResult(){
        // 利用lua的table

        // "return {1, 2, 3}" -> list.size = 3
        // "return {1, nil, 3}" -> list.size = 1

        // mset k1 1 k2 2 k3 3  -> 正确返回 1, (nil), 2  -> list.size = 3, list.get(1) = null
        String script =
              "local rs = {}; "
            + "rs[1]=redis.call('get','k1'); "
            + "rs[2]=redis.call('get', 'k4'); "
            + "rs[3]=redis.call('get', 'k2'); "
            + "return rs;";


        List<Object> eval = (List<Object>) jedisPool.getResource().eval(script, 0);


        System.out.println(eval);
    }

}
