package com.vergilyn.examples;

import java.util.Date;

import com.vergilyn.examples.cache.VoteCache;
import com.vergilyn.examples.entity.VoteItem;
import com.vergilyn.examples.entity.VoteLog;

import org.springframework.beans.factory.annotation.Autowired;
import org.testng.annotations.Test;

/**
 * @author VergiLyn
 * @date 2019-06-24
 */
public class VoteTest extends BasicTestng {
    @Autowired
    private VoteCache voteCache;

    @Test
    public void vote(){
        VoteItem item = new VoteItem();
        item.setId(1001L);
        item.setVoteId(1L);

        VoteLog voteLog = new VoteLog();
        voteLog.setVoteTime(new Date());

        System.out.println(voteCache.incrCount(item, voteLog));
    }
}
