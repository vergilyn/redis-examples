package com.vergilyn.examples.redisson.service;

import com.vergilyn.examples.redisson.annotation.RedissonLockAnno;
import com.vergilyn.examples.redisson.annotation.RedissonLockKey;
import com.vergilyn.examples.redisson.annotation.RedissonLockType;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

/**
 * @author VergiLyn
 * @blog http://www.cnblogs.com/VergiLyn/
 * @date 2017/12/17
 */
@Component
public class FairLockService {
    private static final Logger logger = LoggerFactory.getLogger(FairLockService.class);

    @RedissonLockAnno(prefix = "fair_lock_id", type = RedissonLockType.FAIR_LOCK)
    public String fairLock(@RedissonLockKey String id){
        Thread thread = Thread.currentThread();
        long tid = thread.getId();
        String tname = thread.getName();
        long last = Long.valueOf(tname.substring(tname.length() - 1, tname.length()));

        String rs = String.format("id: %s, tid: %s, tname: %s, last: %d", id, tid, tname, last);
        try {
            logger.info("fairLock(String id)" + rs);
            Thread.sleep(6 * 1000);
        } catch (InterruptedException e) {
            e.printStackTrace();
        }
        return rs;
    }
}
