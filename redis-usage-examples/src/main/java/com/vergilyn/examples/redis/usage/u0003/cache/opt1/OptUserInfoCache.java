package com.vergilyn.examples.redis.usage.u0003.cache.opt1;

import java.util.concurrent.TimeUnit;

import com.vergilyn.examples.commons.redis.RedisClientFactory;
import com.vergilyn.examples.redis.usage.u0003.cache.AbstractUserInfoCache;
import com.vergilyn.examples.redis.usage.u0003.entity.UserInfoEntity;
import com.vergilyn.examples.redis.usage.u0003.service.UserInfoService;

import org.springframework.data.redis.core.RedisTemplate;

/**
 * 其实只是简单的提取出了一个模版方法，当增加`openid`时，还是需要：
 * <pre>
 *   1) `OptUserInfoCache` 增加 key-generator
 *   2) `OptUserInfoCache` 增加 `getByOpenid`
 *   3) `setCache` 增加
 * </pre>
 * @author vergilyn
 * @since 2021-04-30
 */
public class OptUserInfoCache extends AbstractAliasCache<UserInfoEntity> implements AbstractUserInfoCache {
	protected final UserInfoService userInfoService = new UserInfoService();

	public OptUserInfoCache() {
		super(RedisClientFactory.getInstance()
				.redisTemplate(String.class, UserInfoEntity.class));
	}

	@Override
	public UserInfoEntity getByUserId(Integer userId) {
		return getByCache(keyUserId(userId), () -> userInfoService.queryByUserId(userId), this::setCache);
	}

	@Override
	public UserInfoEntity getByUsername(String username) {
		return getByCache(keyUsername(username), () -> userInfoService.queryByUsername(username), this::setCache);
	}

	@Override
	public UserInfoEntity getByQQ(Long QQ) {
		return getByCache(keyQQ(QQ), () -> userInfoService.queryByQQ(QQ), this::setCache);
	}

	protected String keyUserId(Integer userId){
		return String.format("userinfo:id:" + userId);
	}
	protected String keyUsername(String username){
		return String.format("userinfo:username:" + username);
	}
	protected String keyQQ(Long QQ){
		return String.format("userinfo:qq:" + QQ);
	}

	protected boolean setCache(RedisTemplate<String, UserInfoEntity> redisTemplate, UserInfoEntity entity) {
		redisTemplate.boundValueOps(keyUserId(entity.getUserId())).set(entity, EXPIRED_SECONDS, TimeUnit.SECONDS);
		redisTemplate.boundValueOps(keyUsername(entity.getUsername())).set(entity, EXPIRED_SECONDS, TimeUnit.SECONDS);
		redisTemplate.boundValueOps(keyQQ(entity.getQQ())).set(entity, EXPIRED_SECONDS, TimeUnit.SECONDS);

		return true;
	}
}
