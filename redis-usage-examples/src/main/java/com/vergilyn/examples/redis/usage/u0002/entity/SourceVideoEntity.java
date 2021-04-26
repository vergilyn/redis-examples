package com.vergilyn.examples.redis.usage.u0002.entity;

import com.vergilyn.examples.redis.usage.u0002.cache.RecentlyUseCache.SourceTypeEnum;

import lombok.Data;

import static com.vergilyn.examples.redis.usage.u0002.cache.RecentlyUseCache.SourceTypeEnum.IMAGE;

@Data
public class SourceVideoEntity extends AbstractIntegerEntity {

	public SourceVideoEntity(Integer id) {
		super(id);
	}

	@Override
	protected SourceTypeEnum getSourceType() {
		return IMAGE;
	}

}
