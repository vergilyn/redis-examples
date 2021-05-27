package com.vergilyn.examples.redis.usage.u0100.service.impl;

import java.util.Date;

import com.vergilyn.examples.redis.usage.u0100.cache.VoteCache;
import com.vergilyn.examples.redis.usage.u0100.entity.Vote;
import com.vergilyn.examples.redis.usage.u0100.service.VoteService;

import org.springframework.beans.factory.annotation.Autowired;

/**
 * @author VergiLyn
 * @date 2019-06-26
 */
// @Service
public class VoteServiceImpl implements VoteService {

    @Autowired
    private VoteCache voteCache;

    @Override
    public Vote saveOrUpdate(Long id, String title, Date beginTime, Date endTime) {
        Vote vote = new Vote(id, title, beginTime, endTime);
        // vote = voteRepository.save(vote);  // 写入数据库

        voteCache.adjustCountExpire(vote);
        return vote;
    }
}
