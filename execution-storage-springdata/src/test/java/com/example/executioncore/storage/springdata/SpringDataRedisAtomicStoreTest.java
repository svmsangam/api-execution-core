package com.example.executioncore.storage.springdata;

import com.example.executioncore.domain.storage.AtomicStore;
import com.example.executioncore.storage.AbstractAtomicStoreContractTest;
import com.example.executioncore.storage.RedisTestContainer;
import org.junit.jupiter.api.BeforeAll;
import org.springframework.data.redis.connection.RedisStandaloneConfiguration;
import org.springframework.data.redis.connection.lettuce.LettuceConnectionFactory;
import org.springframework.data.redis.core.StringRedisTemplate;

public class SpringDataRedisAtomicStoreTest extends AbstractAtomicStoreContractTest {

    private static StringRedisTemplate redisTemplate;
    private static SpringDataRedisAtomicStore store;

    @BeforeAll
    static void init() {
        RedisStandaloneConfiguration config = new RedisStandaloneConfiguration(
                RedisTestContainer.getHost(),
                RedisTestContainer.getPort()
        );
        LettuceConnectionFactory factory = new LettuceConnectionFactory(config);
        factory.afterPropertiesSet();

        redisTemplate = new StringRedisTemplate(factory);
        redisTemplate.afterPropertiesSet();

        store = new SpringDataRedisAtomicStore(redisTemplate);
    }

    @Override
    protected AtomicStore getStore() {
        return store;
    }

    @Override
    protected void clearKeys() {
        redisTemplate.getConnectionFactory().getConnection().serverCommands().flushDb();
    }
}