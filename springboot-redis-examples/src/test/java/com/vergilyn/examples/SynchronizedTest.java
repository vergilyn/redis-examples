package com.vergilyn.examples;

import java.util.Map;

import org.testng.annotations.BeforeTest;
import org.testng.annotations.DataProvider;
import org.testng.annotations.Test;
import org.testng.collections.Maps;

/**
 * @author VergiLyn
 * @date 2019-06-27
 */
public class SynchronizedTest {

    private Map<String, Integer> database;
    private Map<String, Integer> cache;

    @BeforeTest
    public void before(){
        database = Maps.newHashMap();
        database.put("1", 1);
        database.put("2", 2);
        database.put("3", 3);
        database.put("4", 4);

        cache = Maps.newHashMap();
        cache.put("1", 1);
    }

    @Test(dataProvider = "dataProvider", threadPoolSize = 5, invocationCount = 50)
    public void test(String key){
        System.out.println(get(key));
    }

    @DataProvider(name = "dataProvider")
    private Object[][] dataProvider(){
        return new Object[][]{{"1"}, {"2"}, {"3"}, {"4"}};
    }

    private int getOne(String key){
        Integer value = cache.get(key);
        if (value == null){
            synchronized (key){
                value = cache.get(key);
                if (value == null){
                    value = database.get(key);
                    System.out.println("database -> key: " + key);

                    if (value != null){
                        cache.put(key, value);
                    }
                }
            }
        }

        return value;
    }

    private int get(String key){
        Integer value = cache.get(key);
        if (value == null){
            return sget(key);
        }

        return value;
    }

    private synchronized Integer sget(String key){
        Integer value = cache.get(key);
        if (value == null){
            value = database.get(key);
            System.out.println("database -> key: " + key);

            if (value != null){
                cache.put(key, value);
            }
        }

        return value;
    }
}
