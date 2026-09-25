package com.example.executioncore.storage.lettuce;

import com.example.executioncore.domain.storage.AtomicStore;
import com.example.executioncore.storage.AbstractAtomicStoreContractTest;
import com.example.executioncore.storage.RedisTestContainer;
import com.example.executioncore.storage.jedis.LettuceAtomicStore;
import io.lettuce.core.RedisClient;
import io.lettuce.core.api.StatefulRedisConnection;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;

public class LettuceAtomicStoreTest extends AbstractAtomicStoreContractTest {

    private static RedisClient redisClient;
    private static StatefulRedisConnection<String, String> connection;
    private static LettuceAtomicStore store;

    @BeforeAll
    static void init() {
        String uri = String.format("redis://%s:%d", RedisTestContainer.getHost(), RedisTestContainer.getPort());
        redisClient = RedisClient.create(uri);
        connection = redisClient.connect();
        store = new LettuceAtomicStore(connection);
    }

    @AfterAll
    static void tearDown() {
        connection.close();
        redisClient.shutdown();
    }

    @Override
    protected AtomicStore getStore() {
        return store;
    }

    @Override
    protected void clearKeys() {
        connection.sync().flushdb();
    }
}