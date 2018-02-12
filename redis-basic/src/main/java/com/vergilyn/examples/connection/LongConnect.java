package com.vergilyn.examples.connection;

import java.util.ArrayList;
import java.util.List;

import com.vergilyn.examples.JedisUtils;

import redis.clients.jedis.Jedis;

/**
 * @author VergiLyn
 * @blog http://www.cnblogs.com/VergiLyn/
 * @date 2017/12/13
 */
public class LongConnect {
    public static void main(String[] args) {
        List<Thread> threads = new ArrayList<>();

        for (int i = 0; i < 10; i++) {
            threads.add(new Thread(){
                @Override
                public void run(){

                    String thread = Thread.currentThread().getName();

                    Jedis jedis = JedisUtils.getJedis();
                    System.out.println("jedis: " + jedis +", client: " + jedis.getClient());
                    jedis.close();
                }
            });
        }

        for (Thread thread : threads){
            thread.start();
        }
        System.out.println(111111111);
    }
}
