package com.vergilyn.examples.redisson.bean;

import java.util.Date;
import java.util.UUID;

import lombok.Data;
import lombok.extern.slf4j.Slf4j;

/**
 * @author VergiLyn
 * @blog http://www.cnblogs.com/VergiLyn/
 * @date 2017/12/17
 */
@Slf4j
@Data
public class PersonBean {
    private String id;
    private String name;
    private int age;
    private Date birthday;

    public PersonBean() {
        this.id = UUID.randomUUID().toString();
    }
}
