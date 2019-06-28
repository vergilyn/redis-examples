package com.vergilyn.examples.entity;

import javax.persistence.Column;
import javax.persistence.Entity;
import javax.persistence.Table;

import lombok.Data;
import lombok.ToString;

/**
 * @author VergiLyn
 * @date 2019-06-24
 */
@Table(name = "vote_item")
@Entity
@Data
@ToString
public class VoteItem extends AbstractEntity {
    @Column(nullable = false)
    private Long voteId;

    @Column(nullable = false)
    private String name;

    @Column(nullable = false)
    private int count = 0;

    @Column(nullable = true)
    private String type;
}
