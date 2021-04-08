package com.vergilyn.examples.redis.cache;

import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

import com.alibaba.fastjson.JSON;
import com.vergilyn.examples.redis.PageRequest;
import com.vergilyn.examples.redis.Tuple;
import com.vergilyn.examples.redis.cache.impl.ImageRecentlyUseCacheImpl;
import com.vergilyn.examples.redis.entity.SourceImageEntity;

import lombok.extern.slf4j.Slf4j;
import org.testng.annotations.BeforeTest;
import org.testng.annotations.Test;

import static com.alibaba.fastjson.serializer.SerializerFeature.PrettyFormat;
import static com.vergilyn.examples.redis.cache.impl.ImageRecentlyUseCacheImpl.DATASOURCE;
import static org.assertj.core.api.Assertions.assertThat;

@Slf4j
public class ImageRecentlyUseTestng {
	private final RecentlyUseCache<SourceImageEntity> _imageCache = new ImageRecentlyUseCacheImpl();
	private final String userId = "vergilyn";

	@BeforeTest
	public void before(){
		_imageCache.delete(userId);
	}

	@Test
	public void add(){
		Set<Integer> used = DATASOURCE.keySet();

		List<String> members = used.stream().map(Object::toString).collect(Collectors.toList());
		_imageCache.add(userId, members);

		long total = _imageCache.getTotal(userId);

		assertThat(total).isEqualTo(members.size());
	}

	@Test(dependsOnMethods = {"add"})
	public void firstPage(){
		PageRequest pageRequest = PageRequest.of(1, 10);
		Tuple<Long, List<SourceImageEntity>> tuple = _imageCache.listSourcePage(userId, pageRequest);

		System.out.println(JSON.toJSONString(tuple, PrettyFormat));

		assertThat(tuple.getFirst()).isEqualTo(DATASOURCE.size());
	}

	@Test(dependsOnMethods = {"add"})
	public void lastPage(){
		PageRequest pageRequest = PageRequest.of(3, 10);
		Tuple<Long, List<SourceImageEntity>> tuple = _imageCache.listSourcePage(userId, pageRequest);

		System.out.println(JSON.toJSONString(tuple, PrettyFormat));

		assertThat(tuple.getFirst()).isEqualTo(DATASOURCE.size());
		assertThat(tuple.getSecond().stream().map(SourceImageEntity::getId))
				.containsExactlyInAnyOrder(14, 13, 12, 11, 10);
	}
}
