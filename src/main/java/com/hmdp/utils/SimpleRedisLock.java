package com.hmdp.utils;

import org.springframework.data.redis.core.StringRedisTemplate;


import java.util.concurrent.TimeUnit;

public class SimpleRedisLock implements ILock {
    private String name;
    private StringRedisTemplate stringRedisTemplate;
    private static final String KEY_PREFIX = "lock:";

    //进行构造人工注入
    public SimpleRedisLock(StringRedisTemplate stringRedisTemplate, String name) {
        this.stringRedisTemplate = stringRedisTemplate;
        this.name = name;
    }

    @Override
    public boolean tryLock(long timeoutSec) {
        //value有线程的标识
        //获取当前线程的唯一标识
        long ThreadID = Thread.currentThread().getId();
        String value = ThreadID + "";
        Boolean lock = stringRedisTemplate.opsForValue().setIfAbsent(KEY_PREFIX + name, value, timeoutSec, TimeUnit.SECONDS);
        //这里如果直接返回lock会有一个自动拆箱的过程，可能会导致空指针异常
        return lock != null && lock; //如果获取锁成功，返回true
    }

    @Override
    public void unLock() {
        //释放锁
        stringRedisTemplate.delete(KEY_PREFIX + name);
    }
}
