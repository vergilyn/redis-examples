# redis-cluster-examples


## Cluster
- [cluster-tutorial](https://redis.io/topics/cluster-tutorial)
- [cluster-tutorial](http://www.redis.cn/topics/cluster-tutorial.html)

[Redis集群方案应该怎么做？](https://www.zhihu.com/question/21419897)
> 当动态添加或减少node节点时，需要将16384个槽做个再分配，槽中的键值也要迁移。当然，这一过程，在目前实现中，还处于半自动状态，需要人工介入。

> Redis集群，要保证16384个槽对应的node都正常工作，如果某个node发生故障，那它负责的slots也就失效，整个集群将不能工作。

[Redis Cluster深入与实践（续）](https://www.jianshu.com/p/ee76b717f88c)
> Redis Cluster是一种服务器Sharding技术，3.0版本开始正式提供。
> Redis Cluster并没有使用一致性hash，而是采用slot(槽)的概念，一共分成16384个槽。
> 将请求发送到任意节点，接收到请求的节点会将查询请求发送到正确的节点上执行。
> 当客户端操作的key没有分配到该node上时，就像操作单一Redis实例一样，当客户端操作的key没有分配到该node上时，Redis会返回转向指令，指向正确的node，这有点儿像浏览器页面的302 redirect跳转。

> Redis集群，要保证16384个槽对应的node都正常工作，如果某个node发生故障，那它负责的slots也就失效，整个集群将不能工作。  
> 为了增加集群的可访问性，官方推荐的方案是将node配置成主从结构，即一个master主节点，挂n个slave从节点。  
> 这时，如果主节点失效，Redis Cluster会根据选举算法从slave节点中选择一个上升为主节点，整个集群继续对外提供服务。


特点：
  - 无中心架构，支持动态扩容，对业务透明
  - 具备Sentinel的监控和自动Failover能力
  - 客户端不需要连接集群所有节点,连接集群中任何一个可用节点即可
  - 高性能，客户端直连redis服务，免去了proxy代理的损耗

缺点：
  - 运维也很复杂
  - 数据迁移需要人工干预 （不确定）
  - 只能使用 db0
  - 不支持批量操作
  - 分布式逻辑和存储模块耦合


**疑问**：
  添加或减少node节点时，slot怎么迁移？


## JedisCluster
较高版本的Jedis(v2.9.3)中提供了有限的**批量操作命令**。
但是，批量操作必须要保证所有的keys在同一个hash-slot。
```
// redis.clients.jedis.JedisClusterCommand.run(int, java.lang.String...)
public T run(int keyCount, String... keys) {
    if (keys == null || keys.length == 0) {
      throw new JedisClusterException("No way to dispatch this command to Redis Cluster.");
    }

    // For multiple keys, only execute if they all share the same connection slot.
    int slot = JedisClusterCRC16.getSlot(keys[0]);
    if (keys.length > 1) {
      for (int i = 1; i < keyCount; i++) {
        int nextSlot = JedisClusterCRC16.getSlot(keys[i]);
        if (slot != nextSlot) {
          throw new JedisClusterException("No way to dispatch this command to Redis Cluster "
              + "because keys have different slots.");
        }
      }
    }

    return runWithRetries(slot, this.maxAttempts, false, false);
  }
```

## spring-data-redis


## 疑问
###
### Cluster MultiKey Command(or Lua script)?
> [cluster-tutorial](https://redis.io/topics/cluster-tutorial)
> Redis Cluster supports multiple key operations as long as all the keys involved into a single command execution  
> (or whole transaction, or Lua script execution) all belong to the same hash slot.  
> The user can force multiple keys to be part of the same hash slot by using a concept called hash tags.
>
> Hash tags are documented in the Redis Cluster specification, but the gist is that if there is a substring between {} brackets in a key,  
> only what is inside the string is hashed, so for example this{foo}key and another{foo}key are guaranteed to be in the same hash slot,  
> and can be used together in a command with multiple keys as arguments.