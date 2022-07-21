package com.vergilyn.examples.data.redisson;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.context.ConfigurableApplicationContext;

/**
 * 项目同时依赖 `spring-data-redis` 和 `spring-data-redisson`
 *
 * @author vergilyn
 * @since 2022-07-08
 */
@SpringBootApplication
public class RedissonDataApplication {

	public static void main(String[] args) {
		SpringApplication application = new SpringApplication(RedissonDataApplication.class);

		ConfigurableApplicationContext context = application.run(args);
	}
}
