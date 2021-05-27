package com.vergilyn.examples.redis.usage.u0100.entity;

import java.io.Serializable;


/**
 * @author VergiLyn
 * @date 2019-06-26
 */
public abstract class AbstractEntity implements Serializable {
    protected Long id;

    protected AbstractEntity() {
    }

    protected AbstractEntity(Long id) {
        this.id = id;
    }

    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }
}
