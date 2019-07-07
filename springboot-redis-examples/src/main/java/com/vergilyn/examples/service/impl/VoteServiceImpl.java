package com.vergilyn.examples.service.impl;

import java.util.Date;

import com.vergilyn.examples.cache.VoteCache;
import com.vergilyn.examples.entity.Vote;
import com.vergilyn.examples.service.VoteService;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

/**
 * @author VergiLyn
 * @date 2019-06-26
 */
@Service
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
