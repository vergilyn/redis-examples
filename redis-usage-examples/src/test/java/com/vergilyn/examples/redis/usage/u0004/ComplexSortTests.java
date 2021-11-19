package com.vergilyn.examples.redis.usage.u0004;

import java.util.Collections;
import java.util.Comparator;
import java.util.Date;
import java.util.List;
import java.util.function.Function;

import com.alibaba.fastjson.JSON;
import com.google.common.collect.Lists;

import lombok.Getter;
import org.junit.jupiter.api.Test;

public class ComplexSortTests {

	@Test
	public void sort(){
		// 未读消息 排在前面，再根据 发送时间 倒序。
		// 且“未读数量”不参与排序！
		Msg read1 = new Msg(0, new Date(1));
		Msg read2 = new Msg(0, new Date(2));
		Msg read3 = new Msg(0, new Date(3));
		Msg unread1 = new Msg(2, new Date(1));
		Msg unread2 = new Msg(1, new Date(2));
		Msg unread3 = new Msg(3, new Date(3));

		List<Msg> msgs = Lists.newArrayList(read1, read2, read3, unread1, unread2, unread3);
		Collections.shuffle(msgs);

		System.out.println("shuffle >>>> " + JSON.toJSONString(msgs));

		// “未读数量” 参与了排序。
		// msgs.sort(Comparator.comparing(Msg::getUnread, Comparator.reverseOrder()).thenComparing(Msg::getSendtime, Comparator.reverseOrder()));

		//  “未读数量” 不参与排序
		msgs.sort(Comparator.comparing((Function<Msg, Integer>) msg -> msg.getUnreadCount() > 0 ? 1 : 0, Comparator.reverseOrder())
				          .thenComparing(Msg::getSendtime, Comparator.reverseOrder()));

		System.out.println("sort >>>> " + JSON.toJSONString(msgs));
	}

	@Getter
	private static class Msg{
		private final Integer unreadCount;
		private final Date sendtime;

		public Msg(Integer unreadCount, Date sendtime) {
			this.unreadCount = unreadCount;
			this.sendtime = sendtime;
		}
	}
}
