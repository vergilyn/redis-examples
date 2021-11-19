package com.vergilyn.examples.redis.usage.u0004;

import java.util.Date;
import java.util.List;
import java.util.Map;
import java.util.concurrent.TimeUnit;

import com.alibaba.fastjson.JSON;
import com.google.common.collect.Lists;
import com.google.common.collect.Maps;
import com.vergilyn.examples.commons.utils.LuaScriptReadUtils;
import com.vergilyn.examples.redis.usage.AbstractRedisClientTest;

import org.apache.commons.lang3.time.DateFormatUtils;
import org.junit.jupiter.api.Test;
import org.springframework.data.redis.core.BoundHashOperations;
import org.springframework.data.redis.core.script.RedisScript;

public class MsgCenterCacheTests extends AbstractRedisClientTest {
	private static final String HKEY_PAGE = "msg_center:%s:%s:page";
	private static final String HKEY_UNREAD_COUNT = "msg_center:%s:%s:unread_count";
	private static final String ZKEY_LATEST_SENDTIME = "msg_center:%s:%s:latest_sendtime";

	private static final int FIXED_SIZE = 10;
	String userId = "409839163";
	String BUSINESS_TYPE_SYSTEM = "system";
	String BUSINESS_TYPE_NOTICE = "notice";
	String BUSINESS_TYPE_ORDER = "order";

	/**
	 * 新增消息：
	 * <pre>
	 * 1. 新增时
	 *   PAGE, `HSET key field value`
	 *   UNREAD_COUNT, `HINCRBY key field 1`
	 *   LATEST_SENDTIME, `ZADD key score member`
	 *
	 * 2. page/unread/latest 都限制最多 100个 entries。 3者绑定(entries 数量和关系应该是一样的)。
	 *
	 * 3. 超过 limit-size时，剔除earliest-sendtime的数据。
	 *  通过 ZSET 的 `ZPOPMIN key [count]`，同时 通过`HDEL`删除 page&unread_count
	 *  （每次可以 `ZPOPMIN key 10`, 避免频繁剔除）
	 *
	 *  `ZPOPMIN` redis-5.0+，可以用`ZRANGEBYSCORE key min max [WITHSCORES] [LIMIT offset count]`替代
	 * </pre>
     */
	@Test
	public void sendMsg(){
		String ruleId = "1024";

		// 1637220110000 -> 2021-11-18 15:21:50
		Date latestSendtime = new Date(1637220110240L);

		String field = entryName(ruleId);

		String keyPage = getKeyPage(userId, BUSINESS_TYPE_SYSTEM);
		String valuePage = valuePage(ruleId, latestSendtime);

		String keyUnreadCount = getKeyUnreadCount(userId, BUSINESS_TYPE_SYSTEM);
		String keyLatestSendtime = getKeyLatestSendtime(userId, BUSINESS_TYPE_SYSTEM);

		String script = LuaScriptReadUtils.getScript(this.getClass(), "send-msg.lua");

		List<String> keys = Lists.newArrayList(keyPage, keyUnreadCount, keyLatestSendtime);

		List<String> args = Lists.newArrayList(
				FIXED_SIZE + "",
				field,
				valuePage,
				latestSendtime.getTime() + "",
				TimeUnit.DAYS.toSeconds(1) + ""
				);

		RedisScript<Long> redisScript = RedisScript.of(script, Long.class);
		Long result = _stringRedisTemplate.execute(redisScript, keys, args.toArray());
		System.out.println(result);
	}

	/**
	 * 需要区分 聚合类 和 非聚合类
	 *
	 * 1) 聚合类消息标记已读(field = rule-id)：整个ruleId 全部已读
	 * 2) 非聚合类(field = record-id)：单个
	 *
	 * <pre>
	 * “标记已读”请求参数：
	 *   {"body":{"dio":
	 *     {"businessType":30,"objectId":"4501600","recordId":"619613c6284a6178364d9528","ruleId":10607}
	 *   }}
	 * </pre>
	 */
	@Test
	public void markRead(){
		String ruleId = "1012";
		String businessType = BUSINESS_TYPE_SYSTEM;  // 聚合类

		String keyUnreadCount = getKeyUnreadCount(userId, businessType);

		// 聚合类-> ruleId   非聚合类-> recordId
		String entryName = entryName(ruleId);

		// 最好：存在 key-field 时，才更新。 否则造成 UNREAD_COUNT 中存在多余 field，无法通过 LATEST_SENDTIME 维护。
		// _stringRedisTemplate.opsForHash().put(keyUnreadCount, entryName, "0");
		BoundHashOperations<String, String, String> hashOps = _stringRedisTemplate.boundHashOps(keyUnreadCount);
		if (Boolean.TRUE.equals(hashOps.hasKey(entryName))){
			hashOps.put(entryName, "0");
		}
	}

	/**
	 * 清空 指定 businessType 的 count。
	 * 直接删除 `UNREAD_COUNT`，因为不知道 FIELD。
	 * 组装列表时记得处理`null = 0`（且）
	 *
	 * <pre>
	 * “一键全读”请求参数：
	 *   {"body":{"dio":{"businessTypes":"30,10","objectId":"4501600"}}}
	 * </pre>
	 */
	@Test
	public void onekeyRead(){
		List<String> businessTypes = Lists.newArrayList(BUSINESS_TYPE_SYSTEM, BUSINESS_TYPE_NOTICE);

		List<String> keys = Lists.newArrayList();
		for (String businessType : businessTypes) {
			keys.add(getKeyUnreadCount(userId, businessType));
		}

		_stringRedisTemplate.delete(keys);
	}

	/**
	 *
	 */
	@Test
	public void complexSort(){

	}
	
	private String entryName(String fieldOrMember){
		return fieldOrMember;
	}

	private String valuePage(String ruleId, Date sendtime){
		String sendtimeStr = DateFormatUtils.format(sendtime, "yyyy-MM-dd HH:mm:ss.SSS");

		Map<String, String> map = Maps.newHashMap();
		map.put("ruleId", ruleId);
		map.put("sendtime", sendtimeStr);
		map.put("content", "content, " + ruleId + "," + sendtimeStr);

		return JSON.toJSONString(map);
	}

	private String getKeyPage(String userId, String businessType){
		return String.format(HKEY_PAGE, userId, businessType);
	}

	private String getKeyUnreadCount(String userId, String businessType){
		return String.format(HKEY_UNREAD_COUNT, userId, businessType);
	}

	private String getKeyLatestSendtime(String userId, String businessType){
		return String.format(ZKEY_LATEST_SENDTIME, userId, businessType);
	}
}
