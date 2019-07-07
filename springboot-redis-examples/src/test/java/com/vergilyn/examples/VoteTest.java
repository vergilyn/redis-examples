package com.vergilyn.examples;

import java.util.Date;

import com.vergilyn.examples.cache.VoteCache;
import com.vergilyn.examples.entity.Vote;
import com.vergilyn.examples.entity.VoteItem;
import com.vergilyn.examples.entity.VoteLog;
import com.vergilyn.examples.service.VoteService;

import org.apache.commons.lang3.time.DateUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.testng.annotations.Test;

/**
 * @author VergiLyn
 * @date 2019-06-24
 */
public class VoteTest extends BasicTestng {
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
}
