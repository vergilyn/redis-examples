package com.vergilyn.examples;

import java.util.List;

import com.alibaba.fastjson.JSON;

import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.dao.DataAccessException;
import org.springframework.data.redis.core.RedisOperations;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.data.redis.core.SessionCallback;
import org.testng.annotations.Test;

/**
 * 1. spring-data-redis默认禁用事务支持。<a href="https://docs.spring.io/spring-data/redis/docs/2.1.6.RELEASE/reference/html/#tx.spring">@Transactional Support</a>
 * <br/>
 * 2. RedisTemplate is not guaranteed to execute all operations in the transaction with the same connection.
 *   (默认，RedisTemplate不能保证使用相同的连接执行事务中的所有操作。)
 *<br/>
 * 3. By default, transaction Support is disabled and has to be explicitly enabled for each RedisTemplate in use by setting setEnableTransactionSupport(true).
 *   默认情况下，事务支持是禁用的，必须通过设置setEnableTransactionSupport(true)显式地为使用中的每个RedisTemplate启用事务支持。
 *<br/>
 *   Doing so forces binding the current RedisConnection to the current Thread that is triggering MULTI.
 *   这样做会强制将当前RedisConnection绑定到触发MULTI的当前线程。
 *<br/>
 *   If the transaction finishes without errors, EXEC is called. Otherwise DISCARD is called.
 *   如果事务完成时没有错误，则调用EXEC。否则将调用丢弃。
 *<br/>
 *   Once in MULTI, RedisConnection queues write operations.
 *   All readonly operations, such as KEYS, are piped to a fresh (non-thread-bound) RedisConnection.
 *   一旦在MULTI中，RedisConnection 队列存在写操作。那么所有的读操作，例如keys/get/mget，都将被piped连接到一个新的的RedisConnection(非线程绑定)
 *
 *
 * @see <a href="https://docs.spring.io/spring-data/redis/docs/2.1.6.RELEASE/reference/html/#tx">Redis Transaction</a>
 * @author VergiLyn
 * @date 2019-05-10
 */
// @RunWith(SpringRunner.class)
@Slf4j
public class SpringDataRedisApplicationTest extends BasicTestng{

    @Autowired
    private RedisTemplate<Object, Object> redisTemplate;

    @Test
    public void tx1(){
        String key = "tx1";
        // stringRedisTemplate 无法保证在同一个connection中执行所有命令。
        stringRedisTemplate.multi();

        stringRedisTemplate.boundValueOps(key).set("1");

      //  stringRedisTemplate.boundHashOps(key).get(key);  // throw RedisCommandExecutionException: WRONGTYPE Operation against a key holding the wrong kind of value

        stringRedisTemplate.boundValueOps(key).set("2");

        List<Object> exec = stringRedisTemplate.exec();

        System.out.println(JSON.toJSONString(exec));
    }

    @Test
    public void tx2(){
        String key = "tx2";
        // 保证在同一个connection中执行所有命令。
        List<Object> txResults = stringRedisTemplate.execute(new SessionCallback<List<Object>>() {

            public List<Object> execute(RedisOperations redisOperations) throws DataAccessException {
                redisOperations.multi();

                redisOperations.boundValueOps(key).set("1");
                redisOperations.boundHashOps(key).get(key);  // throw exception
                redisOperations.boundValueOps(key).set("2");

                // This will contain the results of all operations in the transaction
                return redisOperations.exec();
            }
        });

        System.out.println(JSON.toJSONString(txResults));
    }

}
