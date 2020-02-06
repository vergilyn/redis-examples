# 【003】redis - scan.md

- [redis.cn scan]
- [redis.io scan]
- [github.com redis]

## 1. 特性

### 1.1 **Scan命令的保证**
> [redis.cn scan] 或 [redis.io scan]
>
> SCAN命令以及其他增量式迭代命令（SSCAN、HSCAN、ZSCAN），在进行完整遍历的情况下可以为用户带来以下保证：
>   - 从完整遍历开始直到完整遍历结束期间，一直存在于数据集内的所有元素都会被完整遍历返回；
>     这意味着，如果有一个元素，它从遍历开始直到遍历结束期间都存在于被遍历的数据集当中，那么SCAN命令总会在某次迭代中将这个元素返回给用户。
>   - 同样，如果一个元素在开始遍历之前被移出集合，并且在遍历开始直到遍历结束期间都没有再加入，那么在遍历返回的元素集中就不会出现该元素。
>
> 然而因为增量式命令仅仅使用游标来记录迭代状态， 所以这些命令带有以下缺点：
>   - 同一个元素可能会被返回多次。处理重复元素的工作交由应用程序负责，比如说，可以考虑将迭代返回的元素仅仅用于可以安全地重复执行多次的操作上。
>   - <font style="color: red;background-color:yellow">如果一个元素是在迭代过程中被添加到数据集的，又或者是在迭代过程中从数据集中被删除的，**那么这个元素可能会被返回，也可能不会**</font>。
>     (原文：Elements that were not constantly present in the collection during a full iteration, may be returned or not: it is undefined.)

`scan`原理参考：
  1) [Redis scan命令原理](https://segmentfault.com/a/1190000018218584)  
  **建议结合redis源码仔细理解。**  
  其中提到的一个总结：“redis里边rehash从小到大时，scan系列命令不会重复也不会遗漏.而从大到小时,有可能会造成重复但不会遗漏.”  
  但个人现在还是相信官方文档所说的，可能会存在遗漏。

  2) [redis源码分析与思考（四）——字典遍历与reverse binary iteration算法](https://blog.csdn.net/hackersuye/article/details/82831565)  
  reverse binary iteration(直译：反向二进制迭代)，redis中基于此算法实现SCAN遍历。

  3) [Redis SCAN命令实现有限保证的原理](https://www.cnblogs.com/linxiyue/p/11262969.html)  
  与`1)`差不多。

（重点：结合redis源码，仔细看`1)`、`2)`）

### 1.2 COUNT 选项
SCAN增量式迭代命令**并不保证每次执行都返回某个给定(count)数量的元素，甚至可能会返回零个元素**。但只要命令返回的游标不是 0 ，应用程序就不应该将迭代视作结束。

注意: **并非每次迭代都要使用相同的 COUNT 值 **，用户可以在每次迭代中按自己的需要随意改变 COUNT 值，只要记得将上次迭代返回的游标用到下次迭代里面就可以了。

### 1.3 迭代终止
如果被迭代数据集的大小不断地增长的话，增量式迭代命令可能永远也无法完成一次完整迭代。  
**能否结束一个迭代取决于用户执行迭代的速度是否比数据集增长的速度更快。**

[redis.cn scan]: http://redis.cn/commands/scan.html
[redis.io scan]: https://redis.io/commands/scan
[github.com redis]: https://github.com/antirez/redis
