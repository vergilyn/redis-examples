package com.vergilyn.examples.entity;

import java.util.Date;

import lombok.Data;
import lombok.ToString;

/**
 * @author VergiLyn
 * @date 2019-06-25
 */
@Data
@ToString
public class VoteLog extends AbstractEntity{
    private Long voteId;
    private Long voteItemId;
    private Long userId;
    private Date voteTime;
}
