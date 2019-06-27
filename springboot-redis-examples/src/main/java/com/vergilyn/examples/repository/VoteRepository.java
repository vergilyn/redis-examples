package com.vergilyn.examples.repository;

import com.vergilyn.examples.entity.Vote;

import org.springframework.data.repository.CrudRepository;

/**
 * @author VergiLyn
 * @date 2019-06-26
 */
public interface VoteRepository extends CrudRepository<Vote, Long> {
}
