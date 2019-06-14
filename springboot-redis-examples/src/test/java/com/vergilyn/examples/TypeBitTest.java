package com.vergilyn.examples;

/**
 * @author VergiLyn
 * @date 2019-06-03
 */
public class TypeBitTest {
    public static void main(String[] args) {
        int it = 11;

        System.out.println(Integer.toString(it).length());

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
}
