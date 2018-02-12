package com.vergilyn.examples.redis.junit;

import java.util.concurrent.ConcurrentMap;

import io.netty.util.internal.PlatformDependent;

/**
 * @author VergiLyn
 * @blog http://www.cnblogs.com/VergiLyn/
 * @date 2018/2/6
 */
public class MainTest {
    static int i;

    public static void main(String[] args) {
        ConcurrentMap<String, String> map = PlatformDependent.newConcurrentHashMap();
        map.putIfAbsent("test", "123");
        System.out.println(map.putIfAbsent("test", "124"));
        System.out.println(map.get("test"));
    }
}
