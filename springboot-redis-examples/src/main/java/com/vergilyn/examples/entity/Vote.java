package com.vergilyn.examples.entity;

import java.util.Date;

import javax.persistence.Column;
import javax.persistence.Entity;
import javax.persistence.Table;

import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.ToString;

/**
 * @author VergiLyn
 * @date 2019-06-24
 */
@Table(name = "vote")
@Entity
@NoArgsConstructor
@Data
@ToString
public class Vote extends AbstractEntity {
    @Column(nullable = false)
    private String title;

    @Column(nullable = false)
    private Date beginTime;

    @Column(nullable = false)
    private Date entTime;

    public Vote(Long id, String title, Date beginTime, Date entTime) {
        super(id);
        this.title = title;
        this.beginTime = beginTime;
        this.entTime = entTime;
    }
}
