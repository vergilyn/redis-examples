package com.vergilyn.examples;

import java.time.LocalDate;
import java.util.Date;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

import com.google.common.primitives.Ints;
import com.vergilyn.examples.entity.Vote;

import org.apache.commons.lang3.ArrayUtils;
import org.apache.commons.lang3.math.NumberUtils;
import org.springframework.data.redis.core.script.DigestUtils;
import org.testng.collections.Lists;

/**
 * @author VergiLyn
 * @date 2019-06-03
 */
public class TypeBitTest {
    public static void main(String[] args) throws Exception {
        System.out.println(args);
        Function xxx = new Function<String, Long, Integer, String>() {
            @Override
            public String get(String s, Long l, Integer i) {
                return null;
            }
        };

    }

    private static void group(){
        List<Vote> list = Lists.newArrayList();
        list.add(new Vote(1L));
        list.add(new Vote(2L));
        list.add(new Vote(3L));
        list.add(new Vote(4L));

        Map<Long, Vote> collect = list.stream()
                .collect(Collectors.toMap(Vote::getId, vote -> vote, (o, o2) -> o));
    }

    private static void compare(){
        Long a = 256L;
        Long b = 256L;
        Long c = 300L;
        // null 异常
        System.out.println("a.compareTo(b) >>>> " + a.compareTo(b));  // 0
        System.out.println("a.compareTo(c) >>>> " + a.compareTo(c));  // -1
        System.out.println("c.compareTo(a >>>> " + c.compareTo(a));  // 1
    }

    private static void toInt(){
        String str = "1234a";
        System.out.println(NumberUtils.toInt(str));  // 0，解析异常默认返回0

        // decode, 01234 = 668 ，0x1234 = 4660 多进制
        System.out.println(Integer.decode("0x1234"));  // NumberFormatException

        System.out.println(Integer.valueOf(str));  // NumberFormatException

        System.out.println(Ints.stringConverter().convert(str));  // NumberFormatException
    }

    private static void date() throws Exception {
        LocalDate date1 = LocalDate.of(2019, 7, 2);
        LocalDate date2 = LocalDate.of(2019, 7, 3);

        System.out.println(date1.isBefore(date2));
        System.out.println(date1.isAfter(date2));

        Date begin = new Date();
        Thread.sleep(1000);
        Date end = new Date();

        System.out.println(begin.before(end));
        System.out.println(begin.after(end));
    }

    private static void integerToInt(){
        Integer[] integers = {1, 2, 3, null, 4, 5, null, 6};

        System.out.println(ArrayUtils.toPrimitive(integers));
    }

    private static void sha1(){
        String xx = "return redis.call('incr', 'incr');";
        String redis = DigestUtils.sha1DigestAsHex(xx);
        String apache = org.apache.commons.codec.digest.DigestUtils.sha1Hex(xx);
        System.out.println(redis);
        System.out.println(apache);
    }

    private static void typeBit(){
        System.out.println ("Byte-->>" + "字节数：" + Byte.BYTES + ";位数：" + Byte.SIZE + "; 最小值-->最大值:" + Byte.MIN_VALUE + "-->" + Byte.MAX_VALUE);
        System.out.println ("Short-->>" + "字节数：" + Short.BYTES + ";位数：" + Short.SIZE + "; 最小值-->最大值:" + Short.MIN_VALUE + "-->" + Short.MAX_VALUE);
        System.out.println ("Integer-->>" + "字节数：" + Integer.BYTES + ";位数：" + Integer.SIZE + "; 最小值-->最大值:" + Integer.MIN_VALUE + "-->" + Integer.MAX_VALUE);
        System.out.println ("Long-->>" + "字节数：" + Long.BYTES + ";位数：" + Long.SIZE + "; 最小值-->最大值:" + Long.MIN_VALUE + "-->" + Long.MAX_VALUE);
        System.out.println ("Float-->>" + "字节数：" + Float.BYTES + ";位数：" + Float.SIZE + "; 最小值-->最大值:" + Float.MIN_VALUE + "-->" + Float.MAX_VALUE);
        System.out.println ("Double-->>" + "字节数：" + Double.BYTES + ";位数：" + Double.SIZE + "; 最小值-->最大值:" + Double.MIN_VALUE + "-->" + Double.MAX_VALUE);
        System.out.println ("Character-->>" + "字节数：" + Character.BYTES + ";位数：" + Character.SIZE + "; 最小值-->最大值:" + (int) Character.MIN_VALUE + "-->" + (int) Character.MAX_VALUE);

        /* [JAVA中的几种基本数据类型是什么，各自占用多少字节。](https://blog.csdn.net/zhangyubishoulin/article/details/82423177)

           虽然boolean变现出非0即1的"位"特性，但是存储空间的基本计量单位是字节byte，不是位bit。所以boolean至少占1个字节。
           JVM规范中，boolean变量当作int处理，也就是4个字节；
           而boolean数组当作byte数组处理，即boolean类型的数组里面的每一个元素占1个字节。
         */
    }


    @FunctionalInterface
    interface Function<A, B, C, R>{
        R get(A a, B b, C c);
    }
}
