package com.vergilyn.examples.service;

import java.util.Date;

import com.vergilyn.examples.entity.Vote;

/**
 * @author VergiLyn
 * @date 2019-06-26
 */
public interface VoteService {

    Vote saveOrUpdate(Long id, String title, Date beginTime, Date endTime);

}
