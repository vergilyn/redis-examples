package com.vergilyn.examples.redis.usage.u0002.cache.strategy;

import java.util.List;
import java.util.function.Function;

import com.vergilyn.examples.commons.domain.PageRequest;
import com.vergilyn.examples.commons.domain.Tuple;

/**
 * @author vergilyn
 * @since 2021-05-18
 */
public abstract class AbstractStrategy<ID, T> {
	protected final boolean isStrictlyControlExpired;

	protected final Function<List<T>, List<String>> toMembers;
	protected final Function<List<String>, List<ID>> toId;

	public AbstractStrategy(boolean isStrictlyControlExpired,
			Function<List<T>, List<String>> toMembers,
			Function<List<String>, List<ID>> toId) {

		this.isStrictlyControlExpired = isStrictlyControlExpired;

		this.toMembers = toMembers;
		this.toId = toId;

	}

	public abstract void preparePageQuery(String redisKey, PageRequest pageRequest);

	/**
	 *
	 * @param data
	 *      <br/>&emsp;first: members对应的ids，目的是如果DB存在物理删除，但是redis中还存在该id时需要对redis中当前无效的member进行操作；
	 *      <br/>&emsp;second: 根据`first`从db中获取到的数据；<br/>
	 * @param redisKey
	 * @param pageRequest
	 * @return
	 */
	public abstract List<T> afterPageQuery(Tuple<List<ID>, List<T>> data, String redisKey, PageRequest pageRequest);

	public abstract List<T> prePageQueryCompleted(List<T> data, String redisKey, PageRequest pageRequest);

	public boolean isStrictlyControlExpired() {
		return isStrictlyControlExpired;
	}

	public List<ID> toId(List<String> members){
		if (members == null || members.isEmpty()){
			return null;
		}

		return toId.apply(members);
	}
}
