package com.dataquality.backend.config;

import com.github.benmanes.caffeine.cache.Caffeine;
import org.springframework.boot.autoconfigure.cache.CacheManagerCustomizer;
import org.springframework.cache.annotation.EnableCaching;
import org.springframework.cache.caffeine.CaffeineCacheManager;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.time.Duration;

@Configuration
@EnableCaching
public class CacheConfig {

    /**
     * Short TTL rather than no expiry - the dashboard summary is explicitly
     * evicted on every write that affects it (upload/delete/analyze/reset), but
     * a time-based fallback keeps it from ever going stale for longer than this
     * if an eviction path is ever missed.
     */
    @Bean
    public CacheManagerCustomizer<CaffeineCacheManager> caffeineCacheManagerCustomizer() {
        return cacheManager -> cacheManager.setCaffeine(
                Caffeine.newBuilder().expireAfterWrite(Duration.ofMinutes(2)).maximumSize(100)
        );
    }
}
