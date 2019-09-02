package com.vergilyn.examples.redisson.service;

import com.vergilyn.examples.redisson.annotation.RedissonLockAnno;
import com.vergilyn.examples.redisson.annotation.RedissonLockKey;

import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

/**
 * @author VergiLyn
 * @blog http://www.cnblogs.com/VergiLyn/
 * @date 2017/12/17
 */
@Component
@Slf4j
public class LockService {

    @RedissonLockAnno(prefix = "fair_lock_id", type = RedissonLockAnno.LockType.FAIR_LOCK)
    public String fairLock(@RedissonLockKey String id, long sleepMillis){
        return test(id, sleepMillis);
    }

    @RedissonLockAnno(prefix = "basic_lock_id", type = RedissonLockAnno.LockType.BASIC_LOCK)
    public String basicLock(@RedissonLockKey String id, long sleepMillis){
        return test(id, sleepMillis);
    }

    private String test(String id, long sleepMillis){
        Thread thread = Thread.currentThread();
        long tid = thread.getId();
        String tname = thread.getName();
        long last = Long.valueOf(tname.substring(tname.length() - 1));

        String rs = String.format("id: %s, thread-id: %s, thread-name: %s, last: %d", id, tid, tname, last);
        try {
            log.info("fairLock(String id) >>>> {}", rs);
            Thread.sleep(sleepMillis);
        } catch (Exception e) {
            log.error("", e);
        }
        return rs;
    }
}
