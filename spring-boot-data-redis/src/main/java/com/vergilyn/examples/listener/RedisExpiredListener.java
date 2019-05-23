package com.vergilyn.examples.listener;

import org.springframework.data.redis.connection.Message;
import org.springframework.data.redis.connection.MessageListener;
import org.springframework.stereotype.Component;

/**
 *  key过期事件推送到topic中只有key，无value，因为一旦过期，value就不存在了。
 *  参考redis目录下redis.conf中的"EVENT NOTIFICATION", redis默认的db{0, 15}一共16个数据库
 *     K    Keyspace events, published with __keyspace@<db>__ prefix.
 *     E    Keyevent events, published with __keyevent@<db>__ prefix.
 *
 * <a href="https://docs.spring.io/spring-data/redis/docs/2.1.8.RELEASE/reference/html/#pubsub">5.9. Redis Messaging (Pub/Sub)</a>
 * 监听redis-key expired。
 * @author VergiLyn
 * @date 2019-05-23
 */
@Component
public class RedisExpiredListener implements MessageListener {
    public final static String LISTENER_PATTERN = "__key*__:*";//"__key*__:*";

    /**
     * 客户端监听订阅的topic，当有消息的时候，会触发该方法;
     * 并不能得到value, 只能得到key。
     * 姑且理解为: redis服务在key失效时(或失效后)通知到java服务某个key失效了, 那么在java中不可能得到这个redis-key对应的redis-value。
     * <p>解决方案:
     *  创建copy/shadow key, 例如 set vkey "vergilyn"; 对应copykey: set copykey:vkey "" ex 10;
     *  真正的key是"vkey"(业务中使用), 失效触发key是"copykey:vkey"(其value为空字符为了减少内存空间消耗)。
     *  当"copykey:vkey"触发失效时, 从"vkey"得到失效时的值, 并在逻辑处理完后"del vkey"
     * </p>
     * <p>缺陷:
     *  1: 存在多余的key; (copykey/shadowkey)
     *  2: 不严谨, 假设copykey在 12:00:00失效, 通知在12:10:00收到, 这间隔的10min内程序修改了key, 得到的并不是 失效时的value.
     *  (第1点影响不大; 第2点貌似redis本身的Pub/Sub就不是严谨的, 失效后还存在value的修改, 应该在设计/逻辑上杜绝)
     *  当"copykey:vkey"触发失效时, 从"vkey"得到失效时的值, 并在逻辑处理完后"del vkey"
     * </p>
     * @param message
     * @param bytes
     */
    @Override
    public void onMessage(Message message, byte[] bytes) {
        byte[] body = message.getBody();// 建议使用: valueSerializer
        byte[] channel = message.getChannel();
        System.out.print("onMessage >> " );
        System.out.println(String.format("channel: %s, body: %s, bytes: %s"
                ,new String(channel), new String(body), new String(bytes)));
    }

}
