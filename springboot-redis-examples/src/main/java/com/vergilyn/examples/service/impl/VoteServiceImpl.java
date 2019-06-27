package com.vergilyn.examples.service.impl;

import java.util.Date;

import com.vergilyn.examples.entity.Vote;
import com.vergilyn.examples.repository.VoteRepository;
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
    private VoteRepository voteRepository;

    @Override
    public Vote saveOrUpdate(Long id, String title, Date beginTime, Date endTime) {
        Vote vote = new Vote(id, title, beginTime, endTime);
        vote = voteRepository.save(vote);

        return vote;
    }
}
