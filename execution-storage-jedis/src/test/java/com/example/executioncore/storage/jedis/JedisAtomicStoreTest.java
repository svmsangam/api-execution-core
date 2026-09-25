package com.example.executioncore.storage.jedis;

import com.example.executioncore.domain.storage.AtomicStore;
import com.example.executioncore.storage.AbstractAtomicStoreContractTest;
import com.example.executioncore.storage.RedisTestContainer;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import redis.clients.jedis.Jedis;
import redis.clients.jedis.JedisPool;

public class JedisAtomicStoreTest extends AbstractAtomicStoreContractTest {

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