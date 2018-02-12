package com.vergilyn.examples.junit.redisson;

import org.junit.Before;
import org.junit.runner.RunWith;
import org.junit.runners.BlockJUnit4ClassRunner;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * @author VergiLyn
 * @blog http://www.cnblogs.com/VergiLyn/
 * @date 2018/2/7
 */
@RunWith(BlockJUnit4ClassRunner.class)
public abstract class BaseTest {
    protected Logger logger = null;

    @Before
    public void initLogger(){
        logger = LoggerFactory.getLogger(this.getClass());
    }
}
