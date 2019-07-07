package com.vergilyn.examples.entity;

import lombok.Data;
import lombok.ToString;

/**
 * @author VergiLyn
 * @date 2019-06-24
 */
@Data
@ToString
public class VoteItem extends AbstractEntity {
    private Long voteId;
    private String name;
    private long count = 0;
    private String type;
}
