package com.g93.be;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.data.redis.core.StringRedisTemplate;

@SpringBootTest
public class RedisConnectionTest {

    @Autowired
    private StringRedisTemplate stringRedisTemplate;

    @Test
    public void testConnection() {
        try {
            stringRedisTemplate.opsForValue().set("test_key", "test_value");
            String value = stringRedisTemplate.opsForValue().get("test_key");
            System.out.println("==================================================");
            System.out.println("REDIS CONNECTION TEST PASSED! Value: " + value);
            System.out.println("==================================================");
        } catch (Exception e) {
            System.out.println("==================================================");
            System.out.println("REDIS CONNECTION TEST FAILED!");
            e.printStackTrace();
            System.out.println("==================================================");
            throw e;
        }
    }
}
