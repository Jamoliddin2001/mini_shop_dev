package com.shop.config;

import com.github.benmanes.caffeine.cache.Caffeine;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.cache.CacheManager;
import org.springframework.cache.annotation.EnableCaching;
import org.springframework.cache.caffeine.CaffeineCacheManager;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.time.Duration;

/**
 * Caching configuration (design pattern: <b>Cache-Aside</b>).
 *
 * <p>The product catalog is read-heavy and write-rare (writes are admin-only), which is the textbook
 * case for caching reads and evicting on writes. We use <b>Caffeine</b> — a local, in-JVM cache — kept
 * deliberately small and short-lived.</p>
 *
 * <h2>Why TTL = 60s</h2>
 * <ul>
 *   <li>Caffeine is <b>local to one JVM</b>. {@code @CacheEvict} only clears the node that handled the
 *       write — so with more than one instance, or for changes that bypass the service (Flyway seeds,
 *       direct SQL, future bulk imports), other nodes/paths would serve stale data until the entry
 *       expires. A 60s {@code expireAfterWrite} caps that staleness window.</li>
 *   <li>60s is a balance: for a shop, price/availability staleness measured in minutes is poor UX, so
 *       we do not go to 10 minutes; below ~10s the hit-ratio gain is negligible, so we do not go to 5s.
 *       60s keeps a high hit-ratio on hot pages (page 0, no filter) and absorbs traffic bursts.</li>
 * </ul>
 *
 * <p>{@code maximumSize} bounds memory: the cache key is a composite of (filter, pageable), so the key
 * space is large; we cap entries and let Caffeine evict least-recently-used ones. {@code recordStats}
 * is enabled so Actuator exposes {@code cache.gets{result=hit|miss}} / {@code cache.size}.</p>
 */
@Configuration
@EnableCaching
public class CacheConfig {

    private static final Logger log = LoggerFactory.getLogger(CacheConfig.class);

    /** Cache of paginated/filtered product listings. Referenced by name from the product service. */
    public static final String PRODUCT_LIST_CACHE = "productList";

    private static final Duration TTL = Duration.ofSeconds(60);
    private static final long MAX_SIZE = 500;

    @Bean
    public CacheManager cacheManager() {
        CaffeineCacheManager manager = new CaffeineCacheManager(PRODUCT_LIST_CACHE);
        manager.setCaffeine(Caffeine.newBuilder()
                .expireAfterWrite(TTL)
                .maximumSize(MAX_SIZE)
                .recordStats());
        log.info("Caffeine cache initialised: caches=[{}] ttl={} maxSize={}",
                PRODUCT_LIST_CACHE, TTL, MAX_SIZE);
        return manager;
    }
}
