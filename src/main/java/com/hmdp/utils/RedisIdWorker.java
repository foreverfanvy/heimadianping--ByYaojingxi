package com.hmdp.utils;

import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;

import javax.annotation.Resource;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;

@Component
public class RedisIdWorker {
    @Resource
    private StringRedisTemplate stringRedisTemplate;
    private static final long BEGIN_TIMESTAMP = 1640995200L;
    private static final long COUNT_BITS = 32;

    public long nextId(String keyPrefix) {
        //1.生成时间戳
        LocalDateTime time = LocalDateTime.of(2022, 1, 1, 0, 0, 0);
        long second = time.toEpochSecond(ZoneOffset.UTC);//1640995200L
        LocalDateTime now = LocalDateTime.now();
        long nowsecond = now.toEpochSecond(ZoneOffset.UTC);
        long timestamp = nowsecond - second;
        //2.序列号
        String date = now.format(DateTimeFormatter.ofPattern("yyyy:MM:dd:"));
        long count = stringRedisTemplate.opsForValue().increment("icr:" + keyPrefix + ":" + date);
        //3.拼接
        return timestamp << COUNT_BITS | count;
    }

}
