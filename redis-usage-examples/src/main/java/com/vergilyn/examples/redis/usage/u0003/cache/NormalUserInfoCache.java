package com.vergilyn.examples.redis.usage.u0003.cache;

import java.util.concurrent.TimeUnit;

import com.vergilyn.examples.commons.redis.RedisClientFactory;
import com.vergilyn.examples.redis.usage.u0003.entity.UserInfoEntity;
import com.vergilyn.examples.redis.usage.u0003.service.UserInfoService;

import org.springframework.data.redis.core.BoundValueOperations;
import org.springframework.data.redis.core.RedisTemplate;

/**
 * <pre>
 *   1. entity缓存值存在n份，造成内存浪费，以及可能导致数据不一致。
 *   2. 通过{@linkplain #setCache(UserInfoEntity)}统一设置缓存，可以避免当其它缓存KEY不存在时查询database。
 *   3. 扩展性，如果此时{@linkplain UserInfoEntity} 增加了字段`openid`也需要？（虽然可以复制代码修改）
 *   4. 代码不具备共用性，如果`XxxEntity`也有类似的缓存结构？
 *
 *   5. 能否避免 service提供n个`queryByXxx`？
 * </pre>
 *
 * @author vergilyn
 * @since 2021-04-30
 */
public class NormalUserInfoCache implements AbstractUserInfoCache{
	protected final RedisTemplate<String, UserInfoEntity> redisTemplate = RedisClientFactory.getInstance().redisTemplate();

	protected final UserInfoService userInfoService = new UserInfoService();

	@Override
	public UserInfoEntity getByUserId(Integer userId){
		BoundValueOperations<String, UserInfoEntity> valueOps = redisTemplate.boundValueOps(keyUserId(userId));
		UserInfoEntity entity = valueOps.get();
		if (entity != null){
			return entity;
		}

		entity = userInfoService.queryByUserId(userId);
		if (entity == null){
			return null;
		}

		// valueOps.set(entity, EXPIRED_SECONDS, TimeUnit.SECONDS);
		setCache(entity);

		return entity;
	}

	@Override
	public UserInfoEntity getByUsername(String username){
		BoundValueOperations<String, UserInfoEntity> valueOps = redisTemplate.boundValueOps(keyUsername(username));
		UserInfoEntity entity = valueOps.get();
		if (entity != null){
			return entity;
		}

		entity = userInfoService.queryByUsername(username);
		if (entity == null){
			return null;
		}

		// valueOps.set(entity, EXPIRED_SECONDS, TimeUnit.SECONDS);
		setCache(entity);

		return entity;
	}

	@Override
	public UserInfoEntity getByQQ(Long QQ){
		BoundValueOperations<String, UserInfoEntity> valueOps = redisTemplate.boundValueOps(keyQQ(QQ));
		UserInfoEntity entity = valueOps.get();
		if (entity != null){
			return entity;
		}

		entity = userInfoService.queryByQQ(QQ);
		if (entity == null){
			return null;
		}

		// valueOps.set(entity, EXPIRED_SECONDS, TimeUnit.SECONDS);
		setCache(entity);

		return entity;
	}

	protected void setCache(UserInfoEntity entity){
		redisTemplate.boundValueOps(keyUserId(entity.getUserId()))
					.set(entity, EXPIRED_SECONDS, TimeUnit.SECONDS);

		redisTemplate.boundValueOps(keyUsername(entity.getUsername()))
				.set(entity, EXPIRED_SECONDS, TimeUnit.SECONDS);

		redisTemplate.boundValueOps(keyQQ(entity.getQQ()))
				.set(entity, EXPIRED_SECONDS, TimeUnit.SECONDS);
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
}
