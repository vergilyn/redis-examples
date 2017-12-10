package com.vergilyn.examples.connection;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import com.vergilyn.examples.JedisUtils;

import redis.clients.jedis.Jedis;

/**
 * 同个线程之间不能共享connection, 原因:<br/>
 *   原因是如果共用1个连接, 那么返回的结果无法保证被哪个进程处理. 持有连接的进程理论上都可以对这个连接进行读写, 这样数据就发生错乱了.<br/>
 *  <a href="http://blog.csdn.net/hao508506/article/details/53039345">REDIS实践之请勿踩多进程共用一个实例连接的坑</>
 * @author VergiLyn
 * @blog http://www.cnblogs.com/VergiLyn/
 * @date 2017/12/10
 */
public class TheSameConnection{
    private final static String key = "kky";
    private final static Jedis jedis = JedisUtils.getJedis();

    static{
        jedis.flushAll();
    }

    public static void main(String[] args) {
        System.out.println("main begin....");

        Map<String, String> hash = new HashMap<>();
        hash.put("1", "1");
        hash.put("2", "2");
        hash.put("3", "3");
        hash.put("4", "4");
        hash.put("5", "5");
        hash.put("6", "6");
        hash.put("7", "7");
        hash.put("8", "8");
        hash.put("9", "9");
        hash.put("0", "0");
        jedis.hmset(key, hash);

        List<Thread> threads = new ArrayList<>();

        for (int i = 0; i < 10; i++) {
            threads.add(new Thread(){
                @Override
                public void run(){

                    String thread = Thread.currentThread().getName();
                    String index = thread.substring(thread.length() - 1, thread.length());

                    // 多线程共享connection会出现数据混乱, 或exception.
                    String hget = jedis.hget(key, index);

                    // 当每个线程拥有自己的connection时, 不会出现数据混乱.
                    /*
                    Jedis own = JedisUtils.getJedis();
                    hget = own.hget(key, index);
                    */

                    if(!hget.equals(index)){ // 如果打印了下面
                        System.out.printf("[error] thread: %s, index: %s, hget: %s \r\n", thread, index, hget);
                    }
                }
            });
        }

        for (Thread thread : threads){
            thread.start();
        }
    }

}