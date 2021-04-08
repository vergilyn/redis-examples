package com.vergilyn.examples.redis.entity;

import com.vergilyn.examples.redis.cache.RecentlyUseCache;

import lombok.Data;

@Data
public class SourceImageEntity extends AbstractIntegerEntity {

	public SourceImageEntity(Integer id) {
		super(id);
	}

	@Override
	protected RecentlyUseCache.SourceTypeEnum getSourceType() {
		return RecentlyUseCache.SourceTypeEnum.IMAGE;
	}

}
