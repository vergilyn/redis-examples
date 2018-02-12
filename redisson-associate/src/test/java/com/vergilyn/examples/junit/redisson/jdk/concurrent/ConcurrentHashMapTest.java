package com.vergilyn.examples.junit.redisson.jdk.concurrent;

import java.util.concurrent.ConcurrentHashMap;

import com.vergilyn.examples.junit.redisson.BaseTest;

import org.junit.Test;

/**
 * @author VergiLyn
 * @blog http://www.cnblogs.com/VergiLyn/
 * @date 2018/2/7
 */
public class ConcurrentHashMapTest extends BaseTest {

    private final static ConcurrentHashMap<String, String> map = new ConcurrentHashMap();

    @Test
    public void test(){
        String key = "key_01";

        String oldValue = map.putIfAbsent(key, "value_01");
        System.out.println("old-value: " + oldValue); // old-value -> null

        String current = map.get(key);
        System.out.println("current-value: " + current);
    }


}
