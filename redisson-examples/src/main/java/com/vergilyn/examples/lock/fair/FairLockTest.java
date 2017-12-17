package com.vergilyn.examples.lock.fair;

import com.vergilyn.examples.annotation.FairKey;
import com.vergilyn.examples.annotation.FairLock;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

/**
 * @author VergiLyn
 * @blog http://www.cnblogs.com/VergiLyn/
 * @date 2017/12/17
 */
@Component
public class FairLockTest {
    private static final Logger logger = LoggerFactory.getLogger(FairLockTest.class);

    @FairLock(prefix = "fair_lock_id")
    public String fairLock(@FairKey String id){
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
