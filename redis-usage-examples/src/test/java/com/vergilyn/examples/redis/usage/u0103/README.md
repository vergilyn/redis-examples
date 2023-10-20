# redis-cell

- <https://github.com/brandur/redis-cell>

> redis-cell 是一个用rust语言编写的基于令牌桶算法的的限流模块，
> 提供原子性的限流功能，并允许突发流量，可以很方便的应用于分布式环境中。

## 重点备注
**无法通过redis管理流控使用的 redis-key（根本没有使用redis的key）**

## 安装 redis-cell
**redis-cell 基于 redis 4.0+ 的扩展模块**

1) 下载安装包：<https://github.com/brandur/redis-cell/releases>
2) 将安装包解压到 redis 能访问的目录
3) 进入 redis-cli，执行命令` module load /path/to/libredis_cell.so`;

## Usage
SEE：<https://github.com/brandur/redis-cell/blob/master/README.md>

From Redis (try running `redis-cli`) use the new `CL.THROTTLE` command loaded by
the module. It's used like this:

```
CL.THROTTLE <key> <max_burst> <count per period> <period> [<quantity>]
```

Where `key` is an identifier to rate limit against. Examples might be:

* A user account's unique identifier.
* The origin IP address of an incoming request.
* A static string (e.g. `global`) to limit actions across the entire system.

For example:

```
CL.THROTTLE user123 15 30 60 1
               ▲     ▲  ▲  ▲ ▲
               |     |  |  | └───── apply 1 token (default if omitted)
               |     |  └──┴─────── 30 tokens / 60 seconds
               |     └───────────── 15 max_burst
               └─────────────────── key "user123"
```

### Response

This means that a single token (the `1` in the last parameter) should be
applied against the rate limit of the key `user123`. 30 tokens on the key are
allowed over a 60 second period with a maximum initial burst of 15 tokens. Rate
limiting parameters are provided with every invocation so that limits can
easily be reconfigured on the fly.

The command will respond with an array of integers:

```
127.0.0.1:6379> CL.THROTTLE user123 15 30 60
1) (integer) 0
2) (integer) 16
3) (integer) 15
4) (integer) -1
5) (integer) 2
```

The meaning of each array item is:

1. Whether the action was limited:
    * `0` indicates the action is allowed.
    * `1` indicates that the action was limited/blocked.
2. The total limit of the key (`max_burst` + 1). This is equivalent to the
   common `X-RateLimit-Limit` HTTP header.
3. The remaining limit of the key. Equivalent to `X-RateLimit-Remaining`.
4. The number of seconds until the user should retry, and always `-1` if the
   action was allowed. Equivalent to `Retry-After`.
5. The number of seconds until the limit will reset to its maximum capacity.
   Equivalent to `X-RateLimit-Reset`.

### Multiple Rate Limits

Implement different types of rate limiting by using different key names:

```
CL.THROTTLE user123-read-rate 15 30 60
CL.THROTTLE user123-write-rate 5 10 60
```

## SEE
- 分布式限流 redis-cell: <https://www.jianshu.com/p/1b026b874c40>