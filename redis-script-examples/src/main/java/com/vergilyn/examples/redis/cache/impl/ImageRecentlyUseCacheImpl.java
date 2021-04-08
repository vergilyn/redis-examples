package com.vergilyn.examples.redis.cache.impl;

import java.util.List;
import java.util.Map;

import com.google.common.collect.Lists;
import com.google.common.collect.Maps;
import com.vergilyn.examples.redis.Tuple;
import com.vergilyn.examples.redis.cache.RecentlyUseCache;
import com.vergilyn.examples.redis.entity.SourceImageEntity;

import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.ArrayUtils;
import org.springframework.stereotype.Component;

import static com.vergilyn.examples.redis.cache.RecentlyUseCache.SourceTypeEnum.IMAGE;

@Slf4j
@Component
public class ImageRecentlyUseCacheImpl extends AbstractRecentlyUseCache<SourceImageEntity> {
	public static final Map<Integer, SourceImageEntity> DATASOURCE = Maps.newLinkedHashMap();
	private static final Integer[] invalid = {19, 30, 31, 32, 33};
	static {
		SourceImageEntity temp;
		for (int i = 10; i <= 34; i ++){
			temp = new SourceImageEntity(i);
			temp.setDeleted(ArrayUtils.contains(invalid, i));

			DATASOURCE.put(i, temp);
		}
	}

	@Override
	protected RecentlyUseCache.SourceTypeEnum getSourceType() {
		return IMAGE;
	}

	@Override
	protected List<SourceImageEntity> listByIds(List<Integer> ids) {
		List<SourceImageEntity> result = Lists.newArrayListWithCapacity(ids.size());

		/* 例如 mysql
		 *   SELECT * FROM tb_table WHERE id IN (4, 3, 5);
		 *   最终返回的数据顺序是`3, 4, 5`。
		 *
		 *   可以通过以下sql按IN顺序返回（但个人选择用java代码重新排序）
		 *   SELECT * FROM tb_table WHERE id IN (4, 3, 5) ORDER BY FIELD(`id`, 4, 3, 5)
		 */
		DATASOURCE.forEach((key, value) -> {
			if (ids.contains(key)) {
				result.add(value);
			}
		});

		return result;
	}

	@Override
	protected SourceImageEntity buildInvalidEntity(Integer invalidId) {
		SourceImageEntity sourceImageEntity = new SourceImageEntity(invalidId);
		sourceImageEntity.setDeleted(true);
		return sourceImageEntity;
	}

	@Override
	protected Tuple<List<SourceImageEntity>, List<SourceImageEntity>> filterEntities(List<Integer> expectedIds,
			List<SourceImageEntity> result) {
		Tuple<List<SourceImageEntity>, List<SourceImageEntity>> tuple = splitNormalDeleted(expectedIds, result);

		return tuple;
	}
}
