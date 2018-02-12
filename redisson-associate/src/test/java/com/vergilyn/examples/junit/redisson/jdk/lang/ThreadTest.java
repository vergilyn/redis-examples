package com.vergilyn.examples.junit.redisson.jdk.lang;

import com.vergilyn.examples.junit.redisson.BaseTest;

import org.junit.Test;

/**
 * @author VergiLyn
 * @blog http://www.cnblogs.com/VergiLyn/
 * @date 2018/2/12
 */
public class ThreadTest extends BaseTest{

    /**
     * 标记为中断, 之后的代码一样会执行, 除非调用await()、join()、sleep()等方法会检测中断状态,抛出异常.
     * <pre>
     *    boolean bool = Thread.interrupted(); // 返回当前线程中断标识, 并清除标识;
          Thread.currentThread().interrupt();  // 标记线程: 中断
          boolean bool2 = Thread.currentThread().isInterrupted();// 返回当前线程中断标识; (不清除标识)
     * </pre>
     *
     * @see <a href="https://www.cnblogs.com/mmm950410/p/6121217.html">isInterrupted()方法和Thread.interrupted()方法判断中断状态的区别</a>
     * @see <a href="http://blog.csdn.net/qq_26562641/article/details/51698481">Thread.interrupted()方法的陷阱</a>
     * @see <a href="http://blog.csdn.net/gtuu0123/article/details/6040105">java线程中的interrupt,isInterrupt,interrupted方法</a>
     */
    @Test
    public void test(){

        System.out.println(Thread.interrupted());  // -> false

        Thread.currentThread().interrupt();  // 标记中断, 之后代码依旧执行

        System.out.println(Thread.interrupted());  // -> true, 并清除标识

        System.out.println(Thread.interrupted());  // -> false, 因为标识被清除

        Thread.currentThread().interrupt();

        System.out.println(Thread.currentThread().isInterrupted());  // -> true
        System.out.println(Thread.currentThread().isInterrupted());  // -> true
        System.out.println(Thread.currentThread().isInterrupted());  // -> true, 代码依旧执行

        try {
            Thread.sleep(10 * 1000);  // isInterrupted = true, 调用await()、join()、sleep()等方法时会抛出InterruptedException, 并清除标识
        } catch (InterruptedException e) {
            System.out.println("sleep()... interrupted = " + Thread.currentThread().isInterrupted());  // -> false, 因为被sleep()清除了标识.
        }

    }
}
