package com.vergilyn.examples.redis.usage.u0002.cache;

import java.util.List;

import com.vergilyn.examples.commons.domain.PageRequest;
import com.vergilyn.examples.commons.domain.Tuple;
import com.vergilyn.examples.redis.usage.u0002.entity.AbstractIntegerEntity;

/**
 * 最近使用
 *
 * @author vergilyn
 * @since 2021-04-06
 */
public interface RecentlyUseCache<T extends AbstractIntegerEntity> {

	/**
	 * 分页获取
	 * <pre> VFIXME 2021-04-06 >>>>
	 *   如果考虑排除“无效的”数据，当跳页的时候，
	 *   例如 用户看到total=101，page-size=10，91~101的数据只有`98`有效，
	 *     1) 如果用户跳到 page-index=10，根据补全数据，用户能看到`98`的数据，且 total=91。
	 *     2) 如果用户跳到 page-index=11，没有数据可以补全，且 total=100。如果此时再跳到 page-index=10，只能看到`98`的数据，且 total=91
	 *
	 * </pre>
	 *
	 * @param userId 最近使用者ID
	 * @param pageRequest 页码 从1开始
	 * @return first: 总数； second: 最近使用的资源
	 */
	Tuple<Long, List<T>> listSourcePage(String userId, PageRequest pageRequest);

	/**
	 * 获取 最近使用总数
	 * @param userId 最近使用者ID
	 * @return 最近使用总数
	 */
	long getTotal(String userId);

	/**
	 * 分页获取
	 * @param userId 最近使用者ID
	 * @param pageRequest 页码 从1开始
	 * @return 最近使用的数据
	 */
	List<String> zrevrange(String userId, PageRequest pageRequest);

	/**
	 * 添加到最近使用成功
	 * @param userId 最近使用者ID
	 * @param members 最近使用的资源
	 * @return true, 添加到最近使用成功
	 */
	boolean add(String userId, List<String> members);

	boolean delete(String userId);

	enum SourceTypeEnum {
		IMAGE, VIDEO
	}
}
