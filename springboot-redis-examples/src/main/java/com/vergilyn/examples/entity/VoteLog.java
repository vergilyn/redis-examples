package com.vergilyn.examples.entity;

import java.util.Date;

import javax.persistence.Column;
import javax.persistence.Entity;
import javax.persistence.Table;

import lombok.Data;
import lombok.ToString;

/**
 * @author VergiLyn
 * @date 2019-06-25
 */
@Table(name = "vote_log")
@Entity
@Data
@ToString
public class VoteLog extends AbstractEntity{
    @Column(nullable = false)
    private Long voteId;

    @Column(nullable = false)
    private Long voteItemId;

    @Column(nullable = false)
    private Long userId;

    @Column(nullable = false)
    private Date voteTime;
}
