package com.vergilyn.examples.redis.junit;

import java.util.List;

import com.vergilyn.examples.JedisUtils;

import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.BlockJUnit4ClassRunner;
import redis.clients.jedis.Jedis;
import redis.clients.jedis.Transaction;

/**
 * <a href="http://www.runoob.com/redis/redis-transactions.html">Redis 事务</a>
 * <p>redis事务可以理解为一个打包的批量执行脚本，但批量指令并非原子化的操作，中间某条指令的失败不会导致前面已做指令的回滚，也不会造成后续的指令不做。</p>
 * @author VergiLyn
 * @blog http://www.cnblogs.com/VergiLyn/
 * @date 2018/4/11
 */
@RunWith(BlockJUnit4ClassRunner.class)
public class JedisTransactionTest {

    @Test
    public void jedisTrans(){
        Jedis jedis = JedisUtils.getJedis();
        long start = System.currentTimeMillis();
        Transaction tx = jedis.multi();

        tx.set("trans:1", "1");
        tx.set("trans:2", null); // Jedis检测抛出异常 >> redis.clients.jedis.exceptions.JedisDataException: value sent to redis cannot be null
        tx.set("trans:3", "3");

        List<Object> results = tx.exec();
        long end = System.currentTimeMillis();
        System.out.println("Transaction SET: " + (end - start) + " ms");
        jedis.disconnect();
    }
}
