package com.vergilyn.examples.redis.usage.u0002.cache.strategy;

import java.util.List;
import java.util.function.BiFunction;
import java.util.function.Function;

import com.vergilyn.examples.commons.domain.PageRequest;
import com.vergilyn.examples.commons.domain.Tuple;
import com.vergilyn.examples.redis.usage.u0002.cache.impl.ImageRecentlyUseCacheImpl;

import lombok.extern.slf4j.Slf4j;

@Slf4j
public class FillInvalidDataStrategy<ID, T> extends AbstractStrategy<ID, T>{
	protected final Function<Tuple<List<ID>, List<T>>, Tuple<List<T>, List<T>>> filterEntities;
	protected final BiFunction<String, PageRequest, Tuple<List<ID>, List<T>>> pageQuery;
	protected final BiFunction<String, List<String>, Long> deleteMembers;

	/**
	 * VFIXME 2021-05-18 >>>>
	 *   1. 可以改成builder
	 *   2. 是否可以不用 匿名函数调用？实在不好看，而且过于“臃肿”。其实这一堆 function 等价于 x-interface。
	 *      （现在的想法：{@linkplain ImageRecentlyUseCacheImpl}实现x-interface，本strategy中引用后调用）
	 * @param isStrictlyControlExpired
	 * @param filterEntities
	 * @param pageQuery
	 * @param deleteMembers
	 * @param toMembers
	 * @param toId
	 */
	public FillInvalidDataStrategy(
			boolean isStrictlyControlExpired,
			Function<Tuple<List<ID>, List<T>>, Tuple<List<T>, List<T>>> filterEntities,
			BiFunction<String, PageRequest, Tuple<List<ID>, List<T>>> pageQuery,
			BiFunction<String, List<String>, Long> deleteMembers,
			Function<List<T>, List<String>> toMembers,
			Function<List<String>, List<ID>> toId) {

		super(isStrictlyControlExpired, toMembers, toId);

		this.filterEntities = filterEntities;
		this.pageQuery = pageQuery;
		this.deleteMembers = deleteMembers;
	}

	@Override
	public void preparePageQuery(String redisKey, PageRequest pageRequest) {

	}

	@Override
	public List<T> afterPageQuery(Tuple<List<ID>, List<T>> data, String redisKey, PageRequest pageRequest) {
		Tuple<List<T>, List<T>> filter = filterEntities.apply(data);
		Tuple<List<T>, List<T>> result = fillInvalidData(filter, redisKey, pageRequest);

		List<T> invalid = result.getSecond();
		if (invalid != null && !invalid.isEmpty()){
			deleteMembers.apply(redisKey, toMembers.apply(invalid));
		}

		return result.getFirst();
	}

	@Override
	public List<T> prePageQueryCompleted(List<T> data, String redisKey, PageRequest pageRequest) {
		return data;
	}

	/**
	 * 填补无效的数据
	 * @param source 原始数据，first - validData; second - invalidData
	 * @param redisKey
	 * @param currentPageRequest 当前请求页
	 * @return first - validData; second - invalidData
	 */
	private Tuple<List<T>, List<T>> fillInvalidData(Tuple<List<T>, List<T>> source, String redisKey, PageRequest currentPageRequest){
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
			fill = filterEntities.apply(pageQuery.apply(redisKey, next));

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
}
