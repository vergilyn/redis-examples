package com.vergilyn.examples.redis.usage.u0003.entity;

import lombok.Data;

@Data
public class UserInfoEntity {

	private Integer userId;
	private String username;
	private Long QQ;

	private String nickname;
	private String avatar;

	public static UserInfoEntity newDefault(){
		UserInfoEntity entity = new UserInfoEntity();
		entity.setUserId(10086);
		entity.setUsername("vergilyn");
		entity.setQQ(409839163L);

		entity.setNickname("淡无欲");
		entity.setAvatar("https://pic.cnblogs.com/avatar/1025273/20171112211439.png");

		return entity;
	}
}
