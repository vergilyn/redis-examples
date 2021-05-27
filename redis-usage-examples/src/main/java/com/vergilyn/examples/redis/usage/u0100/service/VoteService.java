package com.vergilyn.examples.redis.usage.u0100.service;

import java.util.Date;

import com.vergilyn.examples.redis.usage.u0100.entity.Vote;

/**
 * @author VergiLyn
 * @date 2019-06-26
 */
public interface VoteService {

    Vote saveOrUpdate(Long id, String title, Date beginTime, Date endTime);

}
