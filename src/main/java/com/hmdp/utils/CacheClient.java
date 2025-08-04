package com.hmdp.utils;

import cn.hutool.core.bean.BeanUtil;
import cn.hutool.core.util.BooleanUtil;
import cn.hutool.core.util.StrUtil;
import cn.hutool.json.JSONObject;
import cn.hutool.json.JSONUtil;
import com.hmdp.entity.Shop;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.function.Function;

import static com.hmdp.utils.RedisConstants.*;
import static com.hmdp.utils.RedisConstants.LOCK_SHOP_KEY;

@Slf4j
@Component
public class CacheClient {

    private final StringRedisTemplate stringRedisTemplate;

    public CacheClient(StringRedisTemplate stringRedisTemplate) {
        this.stringRedisTemplate = stringRedisTemplate;
    }

    public void set(String key, Object value, Long time, TimeUnit timeUnit) {
        try {
            stringRedisTemplate.opsForValue().set(key, JSONUtil.toJsonStr(value), time, timeUnit);
        } catch (Exception e) {
            log.error("Error setting cache for key: {}", key, e);
        }
    }

    public void setWithLogiclExpire(String key, Object value, Long time, TimeUnit timeUnit) {
        try {
            // 设置逻辑过期
            RedisData data = new RedisData();
            data.setExpireTime(LocalDateTime.now().plusSeconds(timeUnit.toSeconds(time)));
            data.setData(value);
            stringRedisTemplate.opsForValue().set(key, JSONUtil.toJsonStr(data));
        } catch (Exception e) {
            log.error("Error setting cache for key: {}", key, e);
        }
    }

    public <R, ID> R queryWithPassThrough(String keyPrefix, ID id, Class<R> type, Function<ID, R> dbFallback, Long time, TimeUnit timeUnit) {
        String key = keyPrefix + id;
        // 1.从Redis中查缓存
        String json = stringRedisTemplate.opsForValue().get(key);
        // 2.缓存命中
        if (StrUtil.isNotBlank(json)) {
            return JSONUtil.toBean(json, type);
        }
        //3.不存在，查询数据库
        if (json != null) {
            return null;// 这里是缓存穿透的情况，直接返回null
        }
        //Shop shop = getById(id);
        //这里是一段逻辑需要被传进来进行调用，要参数中加一段函数式调用
        R r = dbFallback.apply(id);
        //4.数据库不存在，返回错误
        if (r == null) {
            stringRedisTemplate.opsForValue().set(CACHE_SHOP_KEY + id, "", CACHE_NULL_TTL, TimeUnit.MINUTES);
            return null;
        }
        //5.数据库存在，写入Redis缓存
        this.set(key, r, time, timeUnit);
        //6.返回商铺信息
        return r;
    }

    private static final ExecutorService CACHE_REBUILD_EXECUTOR = Executors.newFixedThreadPool(10);//创建线程池
    public <R, ID> R queryWithLogicExpire(String keyPrefix, ID id, Class<R> type, Function<ID, R> dbFallback, Long time, TimeUnit timeUnit) { // 1.从Redis中查缓存
        String key = keyPrefix + id;
        String json = stringRedisTemplate.opsForValue().get(key);

        if (StrUtil.isBlank(json)) {//缓存未命中，返回null
            //查数据库
            R r1 = dbFallback.apply(id);
            //写入Redis
            this.setWithLogiclExpire(key, r1, time, timeUnit);
            return r1;
        }
        // 2.缓存命中->判断过期时间
        LocalDateTime now = LocalDateTime.now();
        RedisData redisData = JSONUtil.toBean(json,RedisData.class);

        JSONObject data = (JSONObject) redisData.getData();//获取data字段
        R r = JSONUtil.toBean(data, type);//将data转换为Shop对象
        LocalDateTime expireTime = redisData.getExpireTime();
        //3.如果缓存未过期，直接返回
        if (expireTime.isAfter(now)) return r;
        //4.缓存已过期
        //4.1.获取互斥锁
        boolean lock = tryLock(LOCK_SHOP_KEY + id);
        //4.2.判断是否获取锁成功
        //4.2.1.成功，开启独立线程，实现缓存重建
        if (lock == true) {
            //获取锁之后还需要进行二次检验，因为可能再获取的时间内进行了更新操作,毕竟有时间的流失虽然很少
            if (expireTime.isAfter(now)) return r;
            //这里最好还是使用线程池来执行任务，避免创建过多线程
            //使用线程池来执行任务
            CACHE_REBUILD_EXECUTOR.submit(() -> {
                try {
                    //查数据库
                    R r1 = dbFallback.apply(id);
                    //写入Redis
                    this.setWithLogiclExpire(key, r1, time, timeUnit);
                } catch (Exception e) {
                    throw new RuntimeException(e);
                } finally {
                    unLock(LOCK_SHOP_KEY + id);
                }
            });
        }
        //4.2.2.失败，直接返回旧数据
        return r;
    }
    private boolean tryLock(String key) {
        //创建锁
        Boolean flag = stringRedisTemplate.opsForValue().setIfAbsent(key, "1", LOCK_SHOP_TTL, TimeUnit.SECONDS);
        return BooleanUtil.isTrue(flag);
    }
    private void unLock(String key) {
        //删除锁
        stringRedisTemplate.delete(key);
    }
}

