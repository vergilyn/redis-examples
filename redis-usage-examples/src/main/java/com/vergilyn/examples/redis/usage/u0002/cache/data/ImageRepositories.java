package com.vergilyn.examples.redis.usage.u0002.cache.data;

import java.util.List;
import java.util.Map;

import com.google.common.collect.Lists;
import com.google.common.collect.Maps;
import com.vergilyn.examples.redis.usage.u0002.entity.SourceImageEntity;

import org.apache.commons.lang3.ArrayUtils;

public class ImageRepositories {
	public static final Map<Integer, SourceImageEntity> DATASOURCE = Maps.newLinkedHashMap();
	public static final Integer[] INVALID = {19, 30, 31, 32, 33};
	static {
		SourceImageEntity temp;
		for (int i = 10; i <= 34; i ++){
			temp = new SourceImageEntity(i);
			temp.setDeleted(ArrayUtils.contains(INVALID, i));

			DATASOURCE.put(i, temp);
		}
	}

	public static List<SourceImageEntity> listByIds(List<Integer> ids){
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

		// 保证result顺序与 ids相同
		result.sort((o1, o2) -> {
			int io1 = ids.indexOf(o1.getId());
			int io2 = ids.indexOf(o2.getId());
			return io1 - io2;
		});

		return result;
	}
}
