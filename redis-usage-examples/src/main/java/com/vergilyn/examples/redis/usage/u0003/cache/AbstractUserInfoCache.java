package com.vergilyn.examples.redis.usage.u0003.cache;

import java.util.concurrent.TimeUnit;

import com.vergilyn.examples.redis.usage.u0003.entity.UserInfoEntity;

/**
 * @author vergilyn
 * @since 2021-04-30
 */
public interface AbstractUserInfoCache {
	Long EXPIRED_SECONDS = TimeUnit.DAYS.toSeconds(1);

	UserInfoEntity getByUserId(Integer userId);

	UserInfoEntity getByUsername(String username);

	UserInfoEntity getByQQ(Long QQ);
}
