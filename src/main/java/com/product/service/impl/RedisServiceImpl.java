package com.product.service.impl;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.product.service.RedisService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.dao.DataAccessException;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.ZSetOperations;
import org.springframework.data.redis.core.script.DefaultRedisScript;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ThreadLocalRandom;
import java.util.function.Supplier;

@Service
@Slf4j
@RequiredArgsConstructor
public class RedisServiceImpl implements RedisService {
    private static final String NULL_VALUE = "__NULL__";
    private static final int BLOOM_SIZE = 1 << 20;
    private static final int[] BLOOM_SEEDS = {17, 31, 53};
    private static final DefaultRedisScript<Long> UNLOCK_SCRIPT =
            new DefaultRedisScript<>(
                    "if redis.call('get', KEYS[1]) == ARGV[1] then "
                            + "return redis.call('del', KEYS[1]) else return 0 end",
                    Long.class);

    private final StringRedisTemplate redisTemplate;
    private final ObjectMapper objectMapper;
    private final Map<String,BitSet> bloomFilters = new ConcurrentHashMap<>();

    // 检查 key 是否存在；Redis 连不上时返回 false，让业务继续走数据库。
    @Override
    public boolean hasKey(String key) {
        try {
            return Boolean.TRUE.equals(redisTemplate.hasKey(key));
        } catch(DataAccessException e) {
            log.warn("Redis读取失败，key={}", key);
            return false;
        }
    }

    // 读取普通字符串；读取失败不会把 Redis 异常抛到业务层。
    @Override
    public String getString(String key) {
        try {
            return redisTemplate.opsForValue().get(key);
        } catch(DataAccessException e) {
            log.warn("Redis读取失败，key={}", key);
            return null;
        }
    }

    // 写入普通字符串和过期时间；写失败只记录日志，不影响主流程。
    @Override
    public void setString(String key, String value, Duration ttl) {
        try {
            redisTemplate.opsForValue().set(key, value, ttl);
        } catch(DataAccessException e) {
            log.warn("Redis写入失败，key={}", key);
        }
    }

    // 批量删除缓存，删除失败时由后续请求自然回源修复缓存。
    @Override
    public void delete(Collection<String> keys) {
        try {
            redisTemplate.delete(keys);
        } catch(DataAccessException e) {
            log.warn("Redis删除失败，keys={}", keys);
        }
    }

    // 给排行榜添加一个成员，底层的 Redis 类型不会泄漏到业务代码。
    @Override
    public void addZSet(String key, String value, double score) {
        try {
            redisTemplate.opsForZSet().add(key, value, score);
        } catch(DataAccessException e) {
            log.warn("Redis ZSet写入失败，key={}", key);
        }
    }

    // 从高分到低分读取排行榜；Redis 异常用 null 告诉业务需要回源。
    @Override
    public List<ScoredValue> reverseRangeWithScores(String key, long start, long end) {
        try {
            Set<ZSetOperations.TypedTuple<String>> tuples = redisTemplate.opsForZSet()
                    .reverseRangeWithScores(key, start, end);
            if(tuples == null) return List.of();
            return tuples.stream()
                    .filter(item -> item.getValue() != null && item.getScore() != null)
                    .map(item -> new ScoredValue(item.getValue(), item.getScore()))
                    .toList();
        } catch(DataAccessException e) {
            log.warn("Redis ZSet读取失败，key={}", key);
            return null;
        }
    }

    // 先清旧排行榜再写新数据，整批成功后设置随机 TTL。
    @Override
    public boolean replaceZSet(String key, Collection<ScoredValue> values,
            Duration minimumTtl, Duration maximumTtl) {
        try {
            redisTemplate.delete(key);
            for(ScoredValue value : values)
                redisTemplate.opsForZSet().add(key, value.value(), value.score());
            if(!values.isEmpty())
                redisTemplate.expire(key, randomDuration(minimumTtl, maximumTtl));
            return true;
        } catch(DataAccessException e) {
            log.warn("Redis ZSet替换失败，key={}", key);
            return false;
        }
    }

    // 给指定 key 设置随机 TTL，避免大量缓存同一时刻过期。
    @Override
    public void expireRandomly(String key, Duration minimumTtl, Duration maximumTtl) {
        try {
            redisTemplate.expire(key, randomDuration(minimumTtl, maximumTtl));
        } catch(DataAccessException e) {
            log.warn("Redis设置过期时间失败，key={}", key);
        }
    }

    // 缓存未命中时只放一个请求回源，其余请求短暂等待缓存结果。
    @Override
    public <T> T getOrLoadWithMutex(String key, Duration minimumTtl, Duration maximumTtl,
            TypeReference<T> type, Supplier<T> loader) {
        T cached = readJson(key, type);
        if(cached != null) return cached;

        String lockKey = key + ":lock";
        String token = UUID.randomUUID().toString();
        boolean locked;
        try {
            locked = Boolean.TRUE.equals(redisTemplate.opsForValue()
                    .setIfAbsent(lockKey, token, Duration.ofSeconds(10)));
        } catch(DataAccessException e) {
            log.warn("Redis不可用，直接回源数据库，key={}", key);
            return loader.get();
        }

        if(!locked) {
            for(int i = 0; i < 5; i++) {
                try {
                    Thread.sleep(30L * (i + 1));
                } catch(InterruptedException e) {
                    Thread.currentThread().interrupt();
                    return loader.get();
                }
                cached = readJson(key, type);
                if(cached != null) return cached;
            }
            return loader.get();
        }

        try {
            // 双重检查
            cached = readJson(key, type);
            if(cached != null) return cached;
            T loaded = loader.get();
            writeJson(key, loaded, minimumTtl, maximumTtl);
            return loaded;
        } finally {
            releaseLock(lockKey, token);
        }
    }

    // 检查是否命中了空值标记，用来快速结束不存在数据的查询。
    @Override
    public boolean hasCachedNull(String key) {
        return NULL_VALUE.equals(getString(key));
    }

    // 写一个短期空值标记，并使用随机 TTL 避免空值缓存一起失效。
    @Override
    public void cacheNull(String key, Duration minimumTtl, Duration maximumTtl) {
        setString(key, NULL_VALUE, randomDuration(minimumTtl, maximumTtl));
    }

    // 同名布隆过滤器只初始化一次，后续请求不会重复查询全部 ID。
    @Override
    public void initializeBloomFilter(String name,
            Supplier<? extends Collection<Long>> valuesLoader) {
        bloomFilters.computeIfAbsent(name, ignored -> {
            BitSet bits = new BitSet(BLOOM_SIZE);
            Collection<Long> values = valuesLoader.get();
            values.stream().filter(Objects::nonNull).forEach(value -> addBloom(bits, value));
            return bits;
        });
    }

    // 布隆过滤器说“不存在”时一定不存在，说“可能存在”时还要查数据库确认。
    @Override
    public boolean mightContain(String name, long value) {
        BitSet bits = bloomFilters.get(name);
        if(bits == null) return true;
        for(int seed : BLOOM_SEEDS) {
            if(!bits.get(bloomIndex(value, seed))) return false;
        }
        return true;
    }

    // 把缓存里的 JSON 还原成调用方需要的类型，脏缓存会被主动删掉。
    private <T> T readJson(String key, TypeReference<T> type) {
        String value = getString(key);
        if(value == null || NULL_VALUE.equals(value)) return null;
        try {
            return objectMapper.readValue(value, type);
        } catch(Exception e) {
            log.warn("Redis缓存反序列化失败，key={}", key);
            delete(List.of(key));
            return null;
        }
    }

    // 把对象转成 JSON 写入缓存，并统一使用随机过期时间。
    private void writeJson(String key, Object value, Duration minimumTtl, Duration maximumTtl) {
        try {
            setString(key, objectMapper.writeValueAsString(value),
                    randomDuration(minimumTtl, maximumTtl));
        } catch(Exception e) {
            log.warn("Redis缓存序列化失败，key={}", key);
        }
    }

    // 只有锁里的令牌还是自己的才释放，防止误删别人的锁。
    private void releaseLock(String lockKey, String token) {
        try {
            redisTemplate.execute(UNLOCK_SCRIPT, List.of(lockKey), token);
        } catch(DataAccessException e) {
            log.warn("Redis锁释放失败，key={}", lockKey);
        }
    }

    // 在给定区间内生成随机 TTL，专门用来缓解缓存雪崩。
    private Duration randomDuration(Duration minimumTtl, Duration maximumTtl) {
        long minimum = minimumTtl.toMillis();
        long maximum = maximumTtl.toMillis();
        if(maximum <= minimum) return minimumTtl;
        return Duration.ofMillis(ThreadLocalRandom.current().nextLong(minimum, maximum + 1));
    }

    // 用多组哈希位置把一个 ID 写进布隆过滤器。
    private void addBloom(BitSet bits, long value) {
        for(int seed : BLOOM_SEEDS) bits.set(bloomIndex(value, seed));
    }

    // 把 ID 和种子混合成稳定的布隆过滤器下标。
    private int bloomIndex(long value, int seed) {
        long hash = value;
        hash ^= hash >>> 33;
        hash *= 0xff51afd7ed558ccdl + seed;
        hash ^= hash >>> 33;
        return (int)Math.floorMod(hash, BLOOM_SIZE);
    }
}
