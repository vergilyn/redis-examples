package com.vergilyn.examples.redis.usage.u0100.entity;

import java.util.Date;

import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.ToString;

/**
 * @author VergiLyn
 * @date 2019-06-24
 */
@NoArgsConstructor
@Data
@ToString
public class Vote extends AbstractEntity {

    private String title;
    private Date beginTime;
    private Date endTime;

    public Vote(Long id) {
        super(id);
    }

    public Vote(Long id, String title, Date beginTime, Date endTime) {
        super(id);
        this.title = title;
        this.beginTime = beginTime;
        this.endTime = endTime;
    }
}
