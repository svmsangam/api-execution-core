package com.example.executioncore.storage;

import org.testcontainers.containers.GenericContainer;
import org.testcontainers.utility.DockerImageName;

public class RedisTestContainer {

    private static final GenericContainer<?> REDIS_CONTAINER =
            new GenericContainer<>(DockerImageName.parse("redis:7.2-alpine"))
                    .withExposedPorts(6379);

    static {
        REDIS_CONTAINER.start();
    }

    public static String getHost() {
        return REDIS_CONTAINER.getHost();
    }

    public static Integer getPort() {
        return REDIS_CONTAINER.getMappedPort(6379);
    }
}