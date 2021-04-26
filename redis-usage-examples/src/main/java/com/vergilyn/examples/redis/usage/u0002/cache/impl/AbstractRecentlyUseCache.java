package com.vergilyn.examples.redis.usage.u0002.cache.impl;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.Collections;
import java.util.List;
import java.util.Set;
import java.util.concurrent.TimeUnit;
import java.util.function.Function;
import java.util.stream.Collectors;

import com.alibaba.fastjson.JSON;
import com.google.common.collect.Lists;
import com.vergilyn.examples.commons.domain.PageRequest;
import com.vergilyn.examples.commons.domain.Tuple;
import com.vergilyn.examples.commons.redis.RedisClientFactory;
import com.vergilyn.examples.redis.usage.u0002.RecentlyUsedOperation;
import com.vergilyn.examples.redis.usage.u0002.cache.RecentlyUseCache;
import com.vergilyn.examples.redis.usage.u0002.entity.AbstractEntity;
import com.vergilyn.examples.redis.usage.u0002.entity.AbstractIntegerEntity;

import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.StringRedisTemplate;

@Slf4j
public abstract class AbstractRecentlyUseCache<T extends AbstractIntegerEntity> implements RecentlyUseCache<T> {
	protected final StringRedisTemplate stringRedisTemplate = RedisClientFactory.getInstance().stringRedisTemplate();

	protected abstract SourceTypeEnum getSourceType();
	protected abstract List<T> listByIds(List<Integer> ids);
	/**
	 * 特别：result.size <= expectedIds.size，例如数据库物理删除（或者数据库不返回逻辑删除的数据）
	 * @param expectedIds 期望获取数据的ids
	 * @param result 根据<code>expectedIds</code> 实际获取到的数据
	 * @return first - 正常数据；second - 无效数据。（first.ids + second.ids = expectedIds）
	 */
	protected abstract Tuple<List<T>, List<T>> filterEntities(List<Integer> expectedIds, List<T> result);

	/**
	 * "最近使用" 最多保留数量
	 */
	private final long _maxSize;

	/**
	 * "最近使用" 最多保留时间
	 */
	private final long _expiredSeconds;

	public AbstractRecentlyUseCache() {
		this(500L, TimeUnit.DAYS.toSeconds(7));
	}

	public AbstractRecentlyUseCache(long maxSize, long expiredSeconds) {
		this._maxSize = maxSize;
		this._expiredSeconds = expiredSeconds;
	}

	@Override
	public Tuple<Long, List<T>> listSourcePage(String userId, PageRequest pageRequest) {
		Tuple<Long, List<T>> page = Tuple.of();

		beforeProcessData(userId, pageRequest);

		Tuple<List<T>, List<T>> data = listSourcePageInner(userId, pageRequest);

		fillInvalidData(data, userId, pageRequest);
		removeCacheInvalidData(data.getSecond(), userId);

		page.setSecond(data.getFirst());

		// 如果过滤数据，会导致total减小，所以为了保证total的准确性，最后才获取total
		page.setFirst(getTotal(userId));

		return page;
	}

	protected void beforeProcessData(String userId, PageRequest pageRequest){
		// 严格控制每个资源最近使用的失效时间（不只由key控制）
		double minScore = buildScore(LocalDateTime.now().plusSeconds(-getExpiredSeconds()));
		stringRedisTemplate.boundZSetOps(key(userId)).removeRangeByScore(0, minScore);
	}

	protected final Tuple<List<T>, List<T>> listSourcePageInner(String userId, PageRequest pageRequest){
		List<String> members = zrevrange(userId, pageRequest);
		if (members == null || members.isEmpty()){
			return Tuple.of(Collections.emptyList(), Collections.emptyList());
		}

		// VFIXME 2021-04-08 以下代码未解耦，子类并不好扩展。

		List<Integer> ids = members.stream().map(Integer::valueOf).collect(Collectors.toList());

		// 子类实现 listByIds
		List<T> result = listByIds(ids);

		// 保证result顺序与 ids相同
		result.sort((o1, o2) -> {
			int io1 = ids.indexOf(o1.getId());
			int io2 = ids.indexOf(o2.getId());
			return io1 - io2;
		});

		return filterEntities(ids, result);
	}

	/**
	 * 填补无效的数据
	 * @param source 原始数据，first - validData; second - invalidData
	 * @param userId 查询条件
	 * @param currentPageRequest 当前请求页
	 * @return first - validData; second - invalidData
	 */
	private Tuple<List<T>, List<T>> fillInvalidData(Tuple<List<T>, List<T>> source, String userId, PageRequest currentPageRequest){
		List<T> invalid = source.getSecond();
		if (invalid.isEmpty()){
			return source;
		}

		List<T> valid = source.getFirst();
		if (currentPageRequest.getSize() - valid.size() <= 0){
			return source;
		}

		PageRequest next = currentPageRequest;
		Tuple<List<T>, List<T>> fill;
		List<T> validFill, invalidFill;
		do {
			next = next.next();
			fill = listSourcePageInner(userId, next);
			validFill = fill.getFirst();
			invalidFill = fill.getSecond();

			if (validFill.isEmpty() && invalidFill.isEmpty()){
				break;
			}

			if (!invalidFill.isEmpty()){
				// 无效的数据 会全部移除
				invalid.addAll(invalidFill);
			}

			if (!validFill.isEmpty()){
				valid.addAll(validFill);
			}

			if (valid.size() > currentPageRequest.getSize()){
				valid = valid.subList(0, currentPageRequest.getSize());
				source.setFirst(valid);
				break;
			}else if(valid.size() == currentPageRequest.getSize()){
				break;
			}

		}while (valid.size() < currentPageRequest.getSize());

		return source;
	}

	private long removeCacheInvalidData(List<T> deletes, String userId){
		if (deletes.isEmpty()){
			return 0L;
		}

		Object[] members = deletes.stream().map(t -> t.getId().toString()).distinct().toArray();
		Long zrem = stringRedisTemplate.boundZSetOps(key(userId)).remove(members);
		long actual = zrem == null ? 0L : zrem;

		if (log.isInfoEnabled()){
			log.info("[vergilyn]remove invalid-data >>>> userId: {}, actual-del-members: {}, members: {}",
					userId, actual, JSON.toJSONString(members));
		}

		return actual;
	}

	@Override
	public long getTotal(String userId) {
		Long count = stringRedisTemplate.boundZSetOps(key(userId)).zCard();

		return count == null ? 0L : count;
	}

	@Override
	public List<String> zrevrange(String userId, PageRequest pageRequest) {
		long start = (long) (pageRequest.getIndex() - 1) * pageRequest.getSize();
		long end = start + pageRequest.getSize() - 1;

		Set<String> range = stringRedisTemplate.boundZSetOps(key(userId)).reverseRange(start, end);

		if (range == null || range.isEmpty()){
			return Collections.emptyList();
		}

		return Lists.newArrayList(range);
	}

	@Override
	public boolean add(String userId, List<String> members) {
		if (members == null || members.isEmpty()){
			return true;
		}

		Boolean expire = RecentlyUsedOperation.execute(stringRedisTemplate, key(userId),
										getMaxSize(), getExpiredSeconds(), buildScore(LocalDateTime.now()), members);
		boolean result = expire != null && expire;
		addAfter(result, userId, members);

		return result;
	}

	@Override
	public boolean delete(String userId) {
		Boolean delete = stringRedisTemplate.delete(key(userId));
		return delete != null && delete;
	}

	protected void addAfter(boolean result, String userId, List<String> members){
		log.info("[vergilyn]add {}-recently-used finish >>>> result: {}, userId: {}, source: {}",
							getSourceType().name(), result, userId, JSON.toJSONString(members));
	}

	protected double buildScore(LocalDateTime dateTime){
		return Double.parseDouble(dateTime.format(DateTimeFormatter.ofPattern("yyyyMMddHHmmss")));
	}

	private String key(String userId){
		return String.format("vergilyn:used:%s:%s", getSourceType().name().toLowerCase(), userId);
	}

	/**
	 * "最近使用" 最多保留数量
	 */
	protected long getMaxSize(){
		return this._maxSize;
	}

	/**
	 * "最近使用" 最多保留时间
	 */
	protected long getExpiredSeconds(){
		return this._expiredSeconds;
	}

	/**
	 * @return first - 正常数据；second - 逻辑删除的数据。（first.ids + second.ids = expectedIds）
	 */
	protected Tuple<List<T>, List<T>> splitNormalDeleted(List<Integer> expectedIds, List<T> result, Function<Integer, T> buildInvalid){
		Tuple<List<T>, List<T>> filter = AbstractEntity.filter(result);

		List<T> normal = filter.getFirst();
		List<T> deleted = filter.getSecond();

		// 保证 expectedIds = normal.ids + deleted.ids
		List<Integer> dataIds = Lists.newArrayListWithCapacity(expectedIds.size());
		dataIds.addAll(normal.stream().map(AbstractIntegerEntity::getId).collect(Collectors.toList()));
		dataIds.addAll(deleted.stream().map(AbstractIntegerEntity::getId).collect(Collectors.toList()));

		expectedIds.stream().filter(integer -> !dataIds.contains(integer))
				.forEach(integer -> deleted.add(buildInvalid.apply(integer)));

		return filter;
	}

}
