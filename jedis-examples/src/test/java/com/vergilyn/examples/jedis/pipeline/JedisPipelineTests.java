package com.vergilyn.examples.jedis.pipeline;

import java.util.List;

import com.vergilyn.examples.jedis.AbstractJedisTests;

import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;
import org.junit.jupiter.api.Test;
import redis.clients.jedis.Jedis;
import redis.clients.jedis.Pipeline;
import redis.clients.jedis.util.RedisOutputStream;

@Slf4j
public class JedisPipelineTests extends AbstractJedisTests {

	/**
	 * `incr jedis`  --RESP--> `*2\r\n$3\r\nincr\r\n$5\r\njedis\r\n`, len=25;
	 * jedis-pipeline的client-output-buffer限制：8192 (这个数字也是有意义的，未去了解)。8192 / 25 ≈ 327.68 条命令为一个数据包。
	 *
	 * <b>jedis-pipeline 每次PSH不能保证最后1条RESP命令的完整性，强制按8192(bytes)划分。（lettuce会保证）</b>
	 *
	 * <p>通过wireshark抓包可知，该测试PSH 2次
	 * <ol>
	 *   <li>172	3.661845	127.0.0.1	127.0.0.1	TCP	    8235	64706 → 56379 [PSH, ACK] Seq=15 Ack=8 Win=2619648 Len=8191</li>
	 *   <li>173	3.661903	127.0.0.1	127.0.0.1	TCP	    44	    56379 → 64706 [ACK] Seq=8 Ack=8206 Win=2611456 Len=0</li>
	 *   <li>174	3.661986	127.0.0.1	127.0.0.1	TCP	    78	    64706 → 56379 [PSH, ACK] Seq=8206 Ack=8 Win=2619648 Len=34</li>
	 * </ol>
	 *
	 * @see RedisOutputStream#RedisOutputStream(java.io.OutputStream) jedis默认限制output-buffer=8192。
	 */
	@Test
	public void jedisPipeline(){
		String key = "jedis";

		Jedis jedis = jedis();
		Pipeline pipeline = jedis.pipelined();
		int num = 0, limit = 329;
		List<Object> list;

		while (num++ < limit) {
			pipeline.incr(key);
		}

		list = pipeline.syncAndReturnAll();
		log.info("exec: incr, key: {}, result: {} \r\n", key, StringUtils.join(list, ","));

		pipeline.close();
		jedis.close();
	}


	/**
	 * `get jedis`  --RESP--> `*2\r\n$3\r\nget\r\n$5\r\njedis\r\n`, len=24;
	 * redis --response RESP--> `$3\r\n342\r\n`, len=9.
	 */
	@Test
	public void jedisPipelineGet(){
		String key = "jedis";

		Jedis jedis = jedis();
		Pipeline pipeline = jedis.pipelined();

		int num = 0, limit = 400;
		while (num++ < limit) {
			pipeline.get(key);
		}

		List<Object> list = pipeline.syncAndReturnAll();
		jedis.close();

		log.info("exec: jedis-pipeline-get, key: {}, result: {} \r\n", key, StringUtils.join(list, ","));
	}
}
