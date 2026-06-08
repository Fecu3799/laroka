package com.laroka.backend.shared.config;

import java.util.List;
import java.util.concurrent.TimeUnit;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.cache.CacheManager;
import org.springframework.cache.annotation.EnableCaching;
import org.springframework.cache.caffeine.CaffeineCache;
import org.springframework.cache.support.SimpleCacheManager;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import com.github.benmanes.caffeine.cache.Caffeine;

@Configuration
@EnableCaching
public class CacheConfig {

	@Value("${cache.menu.ttl-seconds:600}")
	private long menuTtlSeconds;

	@Bean
	public CacheManager cacheManager() {
		CaffeineCache menuCache = new CaffeineCache("menu",
			Caffeine.newBuilder()
				.expireAfterWrite(menuTtlSeconds, TimeUnit.SECONDS)
				.build());
		SimpleCacheManager manager = new SimpleCacheManager();
		manager.setCaches(List.of(menuCache));
		return manager;
	}
}
