package com.vergilyn.examples.junit.redisson.jdk.concurrent.atomic;

import java.util.concurrent.atomic.AtomicReference;

import com.vergilyn.examples.junit.redisson.BaseTest;

import org.junit.Test;

/**
 * @author VergiLyn
 * @blog http://www.cnblogs.com/VergiLyn/
 * @date 2018/2/7
 */
public class AtomicReferenceTest extends BaseTest{

    @Test
    public void test(){
        // jdk 原子操作类, 保证原子操作
        final AtomicReference<String> thread = new AtomicReference<String>();

        thread.set("1111");

        System.out.println(thread.get());

        String previous = thread.getAndSet("2222");
        System.out.println("previous: " + previous);

        System.out.println(thread.get());

    }
}
