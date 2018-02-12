#redisson-offical-examples
github: https://github.com/redisson/redisson-examples

redisson-wiki: https://github.com/redisson/redisson/wiki

### 1. locks
　　参考: [分布式锁和同步器](https://github.com/redisson/redisson/wiki/8.-分布式锁和同步器)
#### 1.1 可重入锁（Reentrant Lock）
　　说明: 
#### 1.2 公平锁（Fair Lock）
　　说明: 当多个Redisson客户端线程同时请求加锁时，优先分配给先发出请求的线程。
#### 1.3 联锁（MultiLock）
　　说明: 可以将多个RLock对象关联为一个联锁，每个RLock对象实例可以来自于不同的Redisson实例。
```
RLock lock1 = redissonInstance1.getLock("lock1");
RLock lock2 = redissonInstance2.getLock("lock2");
RLock lock3 = redissonInstance3.getLock("lock3");

RedissonMultiLock lock = new RedissonMultiLock(lock1, lock2, lock3);
// 同时加锁：lock1 lock2 lock3
// 所有的锁都上锁成功才算成功。
lock.lock();
...
lock.unloc
```
#### 1.4 红锁（RedLock）
　　说明: RedLock同MultiLock加锁类似, 但RedLock只要在大部分节点上加锁成功就算成功。
```
RLock lock1 = redissonInstance1.getLock("lock1");
RLock lock2 = redissonInstance2.getLock("lock2");
RLock lock3 = redissonInstance3.getLock("lock3");

RedissonRedLock lock = new RedissonRedLock(lock1, lock2, lock3);
// 同时加锁：lock1 lock2 lock3
// 红锁在大部分节点上加锁成功就算成功。
lock.lock();
...
lock.unlock();
```
#### 1.5 读写锁（ReadWriteLock）
　　说明: 该对象允许同时有多个读取锁，但是最多只能有一个写入锁。