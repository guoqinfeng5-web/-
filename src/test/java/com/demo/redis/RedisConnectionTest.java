package com.demo.redis;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.data.redis.connection.RedisConnection;
import org.springframework.data.redis.connection.RedisConnectionFactory;
import org.springframework.data.redis.core.StringRedisTemplate;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

@SpringBootTest
class RedisConnectionTest {

    @Autowired
    private RedisConnectionFactory redisConnectionFactory;

    @Autowired
    private StringRedisTemplate stringRedisTemplate;

    @Test
    void redisPing_shouldReturnPong() {
        assertNotNull(redisConnectionFactory, "RedisConnectionFactory 不应为空");

        try (RedisConnection connection = redisConnectionFactory.getConnection()) {
            String pong = connection.ping();
            assertEquals("PONG", pong, "Redis ping 应返回 PONG");
        }
    }

    @Test
    void redisSetGet_shouldWork() {
        String key = "demo:test:redis:health";
        String value = "ok";

        stringRedisTemplate.opsForValue().set(key, value);
        String got = stringRedisTemplate.opsForValue().get(key);

        assertEquals(value, got, "Redis set/get 回环应一致");
        assertTrue(Boolean.TRUE.equals(stringRedisTemplate.delete(key)), "测试 key 应可删除");
    }
}

