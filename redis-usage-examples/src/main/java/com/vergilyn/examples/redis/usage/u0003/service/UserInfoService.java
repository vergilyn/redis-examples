package com.vergilyn.examples.redis.usage.u0003.service;

import com.vergilyn.examples.redis.usage.u0003.entity.UserInfoEntity;

public class UserInfoService {
	public static final UserInfoEntity datasource = UserInfoEntity.newDefault();


	public UserInfoEntity queryByUserId(Integer userId){
		if (datasource.getUserId().equals(userId)){
			return datasource;
		}
		return null;
	}

	public UserInfoEntity queryByUsername(String username){
		if (datasource.getUsername().equals(username)){
			return datasource;
		}
		return null;
	}

	public UserInfoEntity queryByQQ(Long QQ){
		if (datasource.getQQ().equals(QQ)){
			return datasource;
		}
		return null;
	}
}
