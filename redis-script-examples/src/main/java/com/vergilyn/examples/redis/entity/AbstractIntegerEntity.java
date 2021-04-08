package com.vergilyn.examples.redis.entity;

import java.util.List;

import com.sun.istack.internal.NotNull;
import com.sun.istack.internal.Nullable;
import com.vergilyn.examples.redis.cache.RecentlyUseCache.SourceTypeEnum;

import lombok.Data;
import org.apache.commons.collections4.CollectionUtils;

import static com.vergilyn.examples.redis.cache.RecentlyUseCache.SourceTypeEnum.IMAGE;

@Data
public abstract class AbstractIntegerEntity extends AbstractEntity<Integer> {

	public AbstractIntegerEntity(Integer id) {
		super(id);
	}

	@Override
	protected String generateTitle(Integer id) {
		return IMAGE.name() + "-" + String.format("%04d", id);
	}

	protected abstract SourceTypeEnum getSourceType();

	/**
	 * 根据 sort 的顺序重新对 entities 排序。
	 * @param entities
	 * @param sort
	 * @return
	 */
	public static <R extends AbstractIntegerEntity> List<R> sort(@NotNull List<R> entities, @Nullable List<Integer> sort){
		if (CollectionUtils.isEmpty(sort)){
			return entities;
		}

		entities.sort((o1, o2) -> {
			int io1 = sort.indexOf(o1.getId());
			int io2 = sort.indexOf(o2.getId());
			return io1 - io2;
		});

		return entities;
	}
}
