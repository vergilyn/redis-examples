package com.vergilyn.examples.redis.autoconfigred;

import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Import;

@Configuration
@Import({ SliceTestRedisConfiguration.class})
public class SliceTestRedisAutoConfiguration {
}
