package com.vergilyn.examples.redis.usage.u0002.cache.impl;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.Collections;
import java.util.List;
import java.util.Set;

import com.alibaba.fastjson.JSON;
import com.google.common.collect.Lists;
import com.vergilyn.examples.commons.domain.PageRequest;
import com.vergilyn.examples.commons.domain.Tuple;
import com.vergilyn.examples.commons.redis.RedisClientFactory;
import com.vergilyn.examples.redis.usage.u0002.RecentlyUsedOperation;
import com.vergilyn.examples.redis.usage.u0002.cache.RecentlyUseCache;
import com.vergilyn.examples.redis.usage.u0002.cache.strategy.AbstractStrategy;
import com.vergilyn.examples.redis.usage.u0002.entity.AbstractEntity;

import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.StringRedisTemplate;

@Slf4j
public abstract class AbstractRecentlyUseCache<ID, T extends AbstractEntity<ID>> implements RecentlyUseCache<T> {
	protected final StringRedisTemplate stringRedisTemplate = RedisClientFactory.getInstance().stringRedisTemplate();

	protected abstract SourceTypeEnum getSourceType();
	protected abstract List<T> listByIds(List<ID> ids);
	protected abstract AbstractStrategy<ID, T> buildDefaultStrategy();

	/**
	 * "最近使用" 最多保留数量
	 */
	private final long _maxSize;

	/**
	 * "最近使用" 最多保留时间
	 */
	private final long _expiredSeconds;

	private final AbstractStrategy<ID, T> strategy;

	public AbstractRecentlyUseCache(long maxSize, long expiredSeconds, AbstractStrategy<ID, T> strategy) {
		this._maxSize = maxSize;
		this._expiredSeconds = expiredSeconds;
		this.strategy = strategy != null ? strategy : buildDefaultStrategy();
	}

	@Override
	public Tuple<Long, List<T>> listSourcePage(String userId, PageRequest pageRequest) {
		String redisKey = key(userId);
		if (strategy.isStrictlyControlExpired()){
			strictlyControlExpired(redisKey);
		}

		strategy.preparePageQuery(redisKey, pageRequest);

		Tuple<List<ID>, List<T>> data = listSourcePageInner(redisKey, pageRequest);

		strategy.afterPageQuery(data, redisKey, pageRequest);

		Tuple<Long, List<T>> page = Tuple.of();
		page.setSecond(data.getSecond());
		// 如果过滤数据，会导致total减小，所以为了保证total的准确性，最后才获取total
		page.setFirst(getTotal(redisKey));

		return page;
	}

	/**
	 * 严格控制每个资源最近使用的失效时间（不只由key控制）
	 * @param redisKey
	 */
	protected void strictlyControlExpired(String redisKey){
		double minScore = buildScore(LocalDateTime.now().plusSeconds(-getExpiredSeconds()));
		stringRedisTemplate.boundZSetOps(redisKey).removeRangeByScore(0, minScore);
	}

	protected final Tuple<List<ID>, List<T>> listSourcePageInner(String redisKey, PageRequest pageRequest){
		List<String> members = zrevrange(redisKey, pageRequest);

		if (members == null || members.isEmpty()){
			return Tuple.of(Collections.emptyList(), Collections.emptyList());
		}

		List<ID> ids = strategy.toId(members);

		// 子类实现 listByIds，且保证`result`顺序与`ids`相同
		List<T> result = listByIds(ids);

		result = strategy.prePageQueryCompleted(result, redisKey, pageRequest);

		return Tuple.of(ids, result);
	}

	@Override
	public long getTotal(String redisKey) {
		Long count = stringRedisTemplate.boundZSetOps(redisKey).zCard();
		return count == null ? 0L : count;
	}

	@Override
	public List<String> zrevrange(String redisKey, PageRequest pageRequest) {
		long start = (long) (pageRequest.getIndex() - 1) * pageRequest.getSize();
		long end = start + pageRequest.getSize() - 1;

		Set<String> range = stringRedisTemplate.boundZSetOps(redisKey).reverseRange(start, end);

		if (range == null || range.isEmpty()){
			return Collections.emptyList();
		}

		return Lists.newArrayList(range);
	}

	public List<String> getAll(String redisKey) {
		Set<String> range = stringRedisTemplate.boundZSetOps(redisKey).reverseRange(0, -1);

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

		String redisKey = key(userId);
		double score = buildScore(LocalDateTime.now());
		Boolean expire = RecentlyUsedOperation.execute(stringRedisTemplate, redisKey,
														getMaxSize(), getExpiredSeconds(), score, members);
		boolean result = expire != null && expire;
		addAfter(result, userId, members);

		return result;
	}

	@Override
	public boolean delete(String userId) {
		Boolean delete = stringRedisTemplate.delete(key(userId));
		return delete != null && delete;
	}

	public long delete(String userId, List<String> members){
		return deleteByKey(key(userId), members);
	}

	public long deleteByKey(String redisKey, List<String> members){
		if (members == null || members.isEmpty()){
			return 0L;
		}

		Long zrem = stringRedisTemplate.boundZSetOps(redisKey).remove(members.toArray());
		long actual = zrem == null ? 0L : zrem;

		if (log.isInfoEnabled()){
			log.info("[vergilyn]remove invalid-data >>>> redis-key: {}, actual-del-members: {}, members: {}",
					redisKey, actual, JSON.toJSONString(members));
		}

		return actual;
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

}
