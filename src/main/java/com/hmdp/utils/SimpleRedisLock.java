package com.hmdp.utils;

import cn.hutool.core.lang.UUID;
import org.springframework.data.redis.core.StringRedisTemplate;


import java.util.concurrent.TimeUnit;

public class SimpleRedisLock implements ILock {
    private String name;
    private StringRedisTemplate stringRedisTemplate;
    private static final String KEY_PREFIX = "lock:";
    private static final String ID_PREFIX = UUID.randomUUID().toString(true) + "";//使用UUID生成一个唯一的ID前缀，避免不同锁之间的冲突

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
        String value = ID_PREFIX + ThreadID + "";
        String key = KEY_PREFIX + name;
        Boolean lock = stringRedisTemplate.opsForValue().setIfAbsent(key, value, timeoutSec, TimeUnit.SECONDS);
        //这里如果直接返回lock会有一个自动拆箱的过程，可能会导致空指针异常
        return lock != null && lock; //如果获取锁成功，返回true
    }

    @Override
    public void unLock() {
        //获取线程标识
        String ThreadID = ID_PREFIX + Thread.currentThread().getId() + "";
        //获取锁的唯一标识
        String lockId = stringRedisTemplate.opsForValue().get(KEY_PREFIX + name);
        if (ThreadID.equals(lockId)) {
            //释放锁
            stringRedisTemplate.delete(KEY_PREFIX + name);
        }

    }
}
