package com.vergilyn.examples.redis.usage.u0002.cache.impl;

import java.util.List;
import java.util.concurrent.TimeUnit;
import java.util.function.Function;
import java.util.stream.Collectors;

import com.google.common.collect.Lists;
import com.vergilyn.examples.commons.domain.Tuple;
import com.vergilyn.examples.redis.usage.u0002.cache.RecentlyUseCache;
import com.vergilyn.examples.redis.usage.u0002.cache.data.ImageRepositories;
import com.vergilyn.examples.redis.usage.u0002.cache.strategy.AbstractStrategy;
import com.vergilyn.examples.redis.usage.u0002.cache.strategy.FillInvalidDataStrategy;
import com.vergilyn.examples.redis.usage.u0002.entity.AbstractEntity;
import com.vergilyn.examples.redis.usage.u0002.entity.AbstractIntegerEntity;
import com.vergilyn.examples.redis.usage.u0002.entity.SourceImageEntity;

import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import static com.vergilyn.examples.redis.usage.u0002.cache.RecentlyUseCache.SourceTypeEnum.IMAGE;

@Slf4j
@Component
public class ImageRecentlyUseCacheImpl extends AbstractRecentlyUseCache<Integer, SourceImageEntity> {

	public ImageRecentlyUseCacheImpl() {
		this(500L, TimeUnit.DAYS.toSeconds(7), null);
	}

	public ImageRecentlyUseCacheImpl(long maxSize, long expiredSeconds) {
		this(maxSize, expiredSeconds, null);
	}

	public ImageRecentlyUseCacheImpl(long maxSize, long expiredSeconds, AbstractStrategy<Integer, SourceImageEntity> strategy) {
		super(maxSize, expiredSeconds, strategy);

	}

	@Override
	public AbstractStrategy<Integer, SourceImageEntity> buildDefaultStrategy(){
		return new FillInvalidDataStrategy<>(false,
				this::filterEntities,
				this::listSourcePageInner,
				this::deleteByKey,
				entities -> entities.stream().map(entity -> entity.getId().toString()).collect(Collectors.toList()),
				members -> members.stream().map(Integer::valueOf).collect(Collectors.toList()));
	}

	@Override
	protected RecentlyUseCache.SourceTypeEnum getSourceType() {
		return IMAGE;
	}

	@Override
	protected List<SourceImageEntity> listByIds(List<Integer> ids) {
		return ImageRepositories.listByIds(ids);
	}

	/**
	 * 特别：result.size <= expectedIds.size，例如数据库物理删除（或者数据库不返回逻辑删除的数据）
	 * @param data 根据<code>expectedIds</code> 实际获取到的数据
	 * @return first - 正常数据；second - 无效数据。（first.ids + second.ids = expectedIds）
	 */
	protected Tuple<List<SourceImageEntity>, List<SourceImageEntity>> filterEntities(Tuple<List<Integer>, List<SourceImageEntity>> data) {

		List<Integer> expectedIds = data.getFirst();
		List<SourceImageEntity> result = data.getSecond();

		Tuple<List<SourceImageEntity>, List<SourceImageEntity>> tuple = splitNormalDeleted(expectedIds, result, invalidId -> {
			SourceImageEntity sourceImageEntity = new SourceImageEntity(invalidId);
			sourceImageEntity.setDeleted(true);
			return sourceImageEntity;
		});

		return tuple;
	}

	/**
	 * vergilyn-comment, 2021-05-18 >>>> 该方法可以是父类方法，也可以是工具类，暂时先提到这里
	 * @param expectedIds
	 * @return first - 正常数据；second - 逻辑删除的数据。（first.ids + second.ids = expectedIds）
	 */
	protected Tuple<List<SourceImageEntity>, List<SourceImageEntity>> splitNormalDeleted(List<Integer> expectedIds,
			List<SourceImageEntity> result, Function<Integer, SourceImageEntity> buildInvalid){
		Tuple<List<SourceImageEntity>, List<SourceImageEntity>> filter = AbstractEntity.filter(result);

		List<SourceImageEntity> normal = filter.getFirst();
		List<SourceImageEntity> deleted = filter.getSecond();

		// 保证 expectedIds = normal.ids + deleted.ids
		List<Integer> dataIds = Lists.newArrayListWithCapacity(expectedIds.size());
		dataIds.addAll(normal.stream().map(AbstractIntegerEntity::getId).collect(Collectors.toList()));
		dataIds.addAll(deleted.stream().map(AbstractIntegerEntity::getId).collect(Collectors.toList()));

		expectedIds.stream().filter(integer -> !dataIds.contains(integer))
				.forEach(integer -> deleted.add(buildInvalid.apply(integer)));

		return filter;
	}
}
