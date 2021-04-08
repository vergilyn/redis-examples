package com.vergilyn.examples.redis.entity;

import java.util.List;

import com.google.common.collect.Lists;
import com.sun.istack.internal.NotNull;
import com.vergilyn.examples.redis.Tuple;

import lombok.Data;

@Data
public abstract class AbstractEntity<ID> {
	protected ID id;
	protected String title;
	protected boolean isDeleted;

	private AbstractEntity(){
	}

	public AbstractEntity(ID id) {
		this.id = id;
		this.title = generateTitle(id);
	}

	protected abstract String generateTitle(ID id);

	/**
	 *
	 * @param entities
	 * @param <R>
	 * @param <ID>
	 * @return first - 正常数据；second - 数据已删除。
	 */
	public static <R extends AbstractEntity<ID>, ID> Tuple<List<R>, List<R>> filter(@NotNull List<R> entities){
		List<R> normal = Lists.newArrayListWithCapacity(entities.size());
		List<R> deleted = Lists.newArrayListWithCapacity(entities.size());

		for (R entity : entities){
			if (entity.isDeleted){
				deleted.add(entity);
			}else {
				normal.add(entity);
			}
		}

		return Tuple.of(normal, deleted);
	}
}
