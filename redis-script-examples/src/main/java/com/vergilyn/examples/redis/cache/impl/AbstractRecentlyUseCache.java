package com.vergilyn.examples.redis.cache.impl;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.Collections;
import java.util.List;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;

import com.alibaba.fastjson.JSON;
import com.google.common.collect.Lists;
import com.vergilyn.examples.redis.PageRequest;
import com.vergilyn.examples.redis.Tuple;
import com.vergilyn.examples.redis.cache.RecentlyUseCache;
import com.vergilyn.examples.redis.entity.AbstractEntity;
import com.vergilyn.examples.redis.entity.AbstractIntegerEntity;
import com.vergilyn.examples.redis.redis.RedisClientFactory;

import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.RedisScript;

@Slf4j
public abstract class AbstractRecentlyUseCache<T extends AbstractIntegerEntity> implements RecentlyUseCache<T> {

	/**
	 * "最近使用" 最多保留数量
	 */
	private static final int MAX_SIZE = 500;

	/**
	 * "最近使用" 最多保留时间
	 */
	private static final int EXPIRED_DAYS_7 = 3600 * 24 * 7;

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

	@Override
	public Tuple<Long, List<T>> listSourcePage(String userId, PageRequest pageRequest) {
		Tuple<Long, List<T>> page = Tuple.of();

		Tuple<List<T>, List<T>> data = listSourcePageInner(userId, pageRequest);

		fillInvalidData(data, userId, pageRequest);
		removeCacheInvalidData(data.getSecond(), userId);

		page.setSecond(data.getFirst());

		// 如果过滤数据，会导致total减小，所以为了保证total的准确性，最后才获取total
		page.setFirst(getTotal(userId));

		return page;
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

		RedisScript<Boolean> script = RedisScript.of(getScript(), Boolean.class);

		List<String> keys = Lists.newArrayList(key(userId));

		List<String> args = Lists.newArrayListWithCapacity(members.size() + 2);
		args.add(MAX_SIZE + "");
		args.add(EXPIRED_DAYS_7 + "");
		args.add(LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyyMMddHHmmss")));
		args.addAll(members);

		Boolean expire = stringRedisTemplate.execute(script, keys, args.toArray());
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

	private String key(String userId){
		return String.format("vergilyn:used:%s:%s", getSourceType().name().toLowerCase(), userId);
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
