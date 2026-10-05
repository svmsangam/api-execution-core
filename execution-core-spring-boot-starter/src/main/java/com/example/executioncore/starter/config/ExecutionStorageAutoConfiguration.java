package com.example.executioncore.starter.config;

import com.example.executioncore.domain.storage.AtomicStore;
import com.example.executioncore.storage.lettuce.JedisAtomicStore;
import com.example.executioncore.storage.lettuce.LettuceAtomicStore;
import com.example.executioncore.storage.springdata.SpringDataRedisAtomicStore;
import io.lettuce.core.api.StatefulRedisConnection;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.redis.core.StringRedisTemplate;
import redis.clients.jedis.JedisPool;

/**
 * Autoconfiguration responsible for detecting available Redis drivers on the classpath
 * and instantiating the appropriate {@link AtomicStore} adapter bean.
 */
@AutoConfiguration
@ConditionalOnProperty(prefix = "execution.core", name = "enabled", havingValue = "true", matchIfMissing = true)
public class ExecutionStorageAutoConfiguration {

    // =========================================================================
    // 1. Spring Data Redis Adapter (Default fallback if storage-type is AUTO or SPRING_DATA)
    // =========================================================================
    @Configuration(proxyBeanMethods = false)
    @ConditionalOnClass(StringRedisTemplate.class)
    @ConditionalOnProperty(prefix = "execution.core", name = "storage-type", havingValue = "SPRING_DATA", matchIfMissing = true)
    static class SpringDataRedisConfiguration {

        @Bean
        @ConditionalOnMissingBean(AtomicStore.class)
        public AtomicStore springDataRedisAtomicStore(StringRedisTemplate redisTemplate) {
            return new SpringDataRedisAtomicStore(redisTemplate);
        }
    }

    // =========================================================================
    // 2. Lettuce Adapter
    // =========================================================================
    @Configuration(proxyBeanMethods = false)
    @ConditionalOnClass(StatefulRedisConnection.class)
    @ConditionalOnProperty(prefix = "execution.core", name = "storage-type", havingValue = "LETTUCE")
    static class LettuceConfiguration {

        @Bean
        @ConditionalOnMissingBean(AtomicStore.class)
        public AtomicStore lettuceAtomicStore(StatefulRedisConnection<String, String> connection) {
            return new LettuceAtomicStore(connection);
        }
    }

    // =========================================================================
    // 3. Jedis Adapter
    // =========================================================================
    @Configuration(proxyBeanMethods = false)
    @ConditionalOnClass(JedisPool.class)
    @ConditionalOnProperty(prefix = "execution.core", name = "storage-type", havingValue = "JEDIS")
    static class JedisConfiguration {

        @Bean
        @ConditionalOnMissingBean(AtomicStore.class)
        public AtomicStore jedisAtomicStore(JedisPool jedisPool) {
            return new JedisAtomicStore(jedisPool);
        }
    }
}