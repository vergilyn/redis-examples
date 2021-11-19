# 【202】案例：多个key关联同一个value

![redis-usage-model](../../redis-usage-model.png)

**功能描述：**
```JSON
{
  "userId": 1,
  "qq": 409839163,
  "mobile": "13800010002",
  "nickname": "vergilyn",
  "avatar": "https://pic.cnblogs.com/avatar/1025273/20171112211439.png"
}
```
例如有以上用户信息，可以通过`userId`、`qq`、`mobile`获取到。
即现在有3个redis-key: `user:userid:1`、`user:qq:409839163`、`user:mobile:13800010002`
都保存了上面这个json对象。

**缺点：**
1. 因为查询条件分别是`userid | qq | mobile`，用上图中最常用的cache使用方式，缓存不存在时，会分别查询1次数据库。
```TXT
public User getByUserId(Integer userid){
    User user = redis.get("user:userid:" + userid);
    if(user == null){
        user = getByDB(userid)
        
        setCacheByUserId(userid, user);
    }
    
    return user;
}

public User getByQQ(Integer qq){
    User user = redis.get("user:qq:" + qq);
    if(user == null){
        user = getByQQ(qq);
        setCacheByQQ(qq, user);
    }
    
    return user;
}

// ...
```

优化思路：
```text
public User getByUserId(Integer userid){
    User user = redis.get("user:userid:" + userid);
    if(user == null){
        user = getByDB(userid)
        
        setCacheByUser(user);
    }
    
    return user;
}

```

2. 内存浪费，现在3个key保存了3个相同的value(且value比较占用内存)。（也可能 数据不一致）


SEE:
  - [redis可以多key对应一个value吗？](https://segmentfault.com/q/1010000005115279)
  - [Link and command? ](https://github.com/redis/redis/issues/2406)
