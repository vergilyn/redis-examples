package com.vergilyn.examples.junit.redisson.jdk.concurrent;

import java.util.concurrent.ConcurrentLinkedQueue;

import com.alibaba.fastjson.JSON;
import com.vergilyn.examples.junit.redisson.BaseTest;

import org.junit.Before;
import org.junit.Test;

/**
 * @author VergiLyn
 * @blog http://www.cnblogs.com/VergiLyn/
 * @date 2018/2/7
 */
public class ConcurrentLinkedQueueTest extends BaseTest{
    private final ConcurrentLinkedQueue<String> queue = new ConcurrentLinkedQueue<>();

    @Before
    public void before(){
        queue.add("1");
        queue.add("2");
        queue.add("3");
        queue.add("4");
    }

    @Test
    public void test(){
        String poll = queue.poll();
        System.out.println("ConcurrentLinkedQueue.poll(): " + poll + ", current: " + JSON.toJSONString(queue));
    }

}
