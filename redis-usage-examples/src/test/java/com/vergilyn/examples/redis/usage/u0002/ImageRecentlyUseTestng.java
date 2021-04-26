package com.vergilyn.examples.redis.usage.u0002;

import java.util.List;
import java.util.Set;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;

import com.alibaba.fastjson.JSON;
import com.google.common.collect.Lists;
import com.vergilyn.examples.commons.domain.PageRequest;
import com.vergilyn.examples.commons.domain.Tuple;
import com.vergilyn.examples.redis.usage.u0002.cache.RecentlyUseCache;
import com.vergilyn.examples.redis.usage.u0002.cache.impl.ImageRecentlyUseCacheImpl;
import com.vergilyn.examples.redis.usage.u0002.entity.SourceImageEntity;

import lombok.SneakyThrows;
import lombok.extern.slf4j.Slf4j;
import org.testng.annotations.BeforeTest;
import org.testng.annotations.Test;

import static com.alibaba.fastjson.serializer.SerializerFeature.PrettyFormat;
import static com.vergilyn.examples.redis.usage.u0002.cache.impl.ImageRecentlyUseCacheImpl.DATASOURCE;
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

		assertThat(tuple.getFirst()).isEqualTo(DATASOURCE.size() - ImageRecentlyUseCacheImpl.INVALID.length);
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

	@SneakyThrows
	@Test
	public void expired(){
		RecentlyUseCache<SourceImageEntity> cache = new ImageRecentlyUseCacheImpl(10, 15);

		cache.add(userId, Lists.newArrayList("10", "11"));
		TimeUnit.SECONDS.sleep(12);

		cache.add(userId, Lists.newArrayList("12", "13"));
		TimeUnit.SECONDS.sleep(4);

		Tuple<Long, List<SourceImageEntity>> tuple = cache.listSourcePage(userId, PageRequest.of(1, 10));

		assertThat(tuple.getFirst()).isEqualTo(2L);
		assertThat(tuple.getSecond().stream().map(SourceImageEntity::getId))
				.containsExactly(13, 12);
	}
}
