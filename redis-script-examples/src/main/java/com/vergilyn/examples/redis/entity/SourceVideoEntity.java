package com.vergilyn.examples.redis.entity;

import com.vergilyn.examples.redis.cache.RecentlyUseCache;

import lombok.Data;

@Data
public class SourceVideoEntity extends AbstractIntegerEntity {

	public SourceVideoEntity(Integer id) {
		super(id);
	}

	@Override
	protected RecentlyUseCache.SourceTypeEnum getSourceType() {
		return RecentlyUseCache.SourceTypeEnum.IMAGE;
	}

}
