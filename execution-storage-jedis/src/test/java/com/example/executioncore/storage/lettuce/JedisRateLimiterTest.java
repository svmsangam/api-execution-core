package com.example.executioncore.storage.lettuce;

import com.example.executioncore.domain.storage.AtomicStore;
import com.example.executioncore.storage.AbstractSlidingWindowRateLimiterContractTest;
import com.example.executioncore.storage.RedisTestContainer;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import redis.clients.jedis.Jedis;
import redis.clients.jedis.JedisPool;

public class JedisRateLimiterTest extends AbstractSlidingWindowRateLimiterContractTest {

    private static JedisPool jedisPool;
    private static JedisAtomicStore store;

    @BeforeAll
    static void init() {
        jedisPool = new JedisPool(RedisTestContainer.getHost(), RedisTestContainer.getPort());
        store = new JedisAtomicStore(jedisPool);
    }

    @AfterAll
    static void tearDown() {
        jedisPool.close();
    }

    @Override
    protected AtomicStore getStore() {
        return store;
    }

    @Override
    protected void clearKeys() {
        try (Jedis jedis = jedisPool.getResource()) {
            jedis.flushDB();
        }
    }
}