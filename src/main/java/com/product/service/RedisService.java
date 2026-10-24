package com.product.service;

import com.fasterxml.jackson.core.type.TypeReference;

import java.time.Duration;
import java.util.Collection;
import java.util.List;
import java.util.function.Supplier;

public interface RedisService {
    record ScoredValue(String value, double score) {}

    // 看看这个 key 在 Redis 里有没有，Redis 挂了就按不存在处理。
    boolean hasKey(String key);
    // 读取一个普通字符串，读不到或者 Redis 异常时返回 null。
    String getString(String key);
    // 写入一个带过期时间的字符串，避免业务层直接操作 Redis 模板。
    void setString(String key, String value, Duration ttl);
    // 一次删除一批缓存 key，常用于数据更新后的缓存失效。
    void delete(Collection<String> keys);
    // 往有序集合里放一个成员和分值，适合排行榜一类数据。
    void addZSet(String key, String value, double score);
    // 按分值从高到低读取有序集合，并返回干净的业务对象。
    List<ScoredValue> reverseRangeWithScores(String key, long start, long end);
    // 用一批新数据整体替换排行榜，并顺手设置随机过期时间。
    boolean replaceZSet(String key, Collection<ScoredValue> values,
            Duration minimumTtl, Duration maximumTtl);
    // 给缓存设置一个区间内的随机过期时间，用来分散集中失效。
    void expireRandomly(String key, Duration minimumTtl, Duration maximumTtl);

    // 先读缓存，没命中时抢互斥锁回源，避免大量请求同时打到数据库。
    <T> T getOrLoadWithMutex(String key, Duration minimumTtl, Duration maximumTtl,
            TypeReference<T> type, Supplier<T> loader);

    // 判断这个 key 是否缓存了“数据确实不存在”的空值标记。
    boolean hasCachedNull(String key);
    // 短时间缓存空值，拦住反复查询不存在数据的请求。
    void cacheNull(String key, Duration minimumTtl, Duration maximumTtl);

    // 应用启动时加载真实 ID；如果预热失败，第一次查询时还可以再次尝试。
    void initializeBloomFilter(String name, Supplier<? extends Collection<Long>> valuesLoader);
    // 快速判断一个 ID 是否可能存在，明确不存在时可以直接拦截。
    boolean mightContain(String name, long value);
}
