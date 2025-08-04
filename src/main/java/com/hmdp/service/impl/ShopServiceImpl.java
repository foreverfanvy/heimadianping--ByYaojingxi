package com.hmdp.service.impl;

import cn.hutool.core.bean.BeanUtil;
import cn.hutool.core.util.BooleanUtil;
import cn.hutool.core.util.StrUtil;
import cn.hutool.json.JSONObject;
import cn.hutool.json.JSONUtil;
import com.hmdp.dto.Result;
import com.hmdp.entity.Shop;
import com.hmdp.mapper.ShopMapper;
import com.hmdp.service.IShopService;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.hmdp.utils.RedisConstants;
import com.hmdp.utils.RedisData;
import lombok.Builder;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import javax.annotation.Resource;
import java.time.LocalDateTime;
import java.util.Map;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

import static com.hmdp.utils.RedisConstants.*;

/**
 * <p>
 * 服务实现类
 * </p>
 *
 * @author 虎哥
 * @since 2021-12-22
 */
@Service
public class ShopServiceImpl extends ServiceImpl<ShopMapper, Shop> implements IShopService {
    @Resource
    private StringRedisTemplate stringRedisTemplate;

    @Override
    public Result queryById(Long id) {
        // 缓存穿透
        //Shop shop = queryWithPassThrough(id);

        //互斥锁解决缓存击穿
        //Shop shop = queryWithMutex(id);

        //逻辑过期解决缓存击穿
        Shop shop = queryWithLogicExpire(id);

        if (shop == null) {
            return Result.fail("店铺不存在");
        }

        return Result.ok(shop);
    }

    private boolean tryLock(String key) {
        //创建锁
        Boolean flag = stringRedisTemplate.opsForValue().setIfAbsent(key, "1", LOCK_SHOP_TTL, TimeUnit.SECONDS);
        return BooleanUtil.isTrue(flag);
    }

    //缓存穿透的代码实现
    public Shop queryWithPassThrough(Long id) throws InterruptedException {
        // 1.从Redis中查缓存
        String shopJson = stringRedisTemplate.opsForValue().get(RedisConstants.CACHE_SHOP_KEY + id);

        // 2.缓存命中
        if (shopJson != null) {
            if (shopJson.isEmpty()) {
                // 缓存的是空对象
                return null;
            }
            // 缓存的是店铺数据
            Shop shop = JSONUtil.toBean(shopJson, Shop.class);
            return shop;
        }
        //3.不存在，查询数据库
        Shop shop = getById(id);
        //4.数据库不存在，返回错误
        if (shop == null) {
            stringRedisTemplate.opsForValue().set(CACHE_SHOP_KEY + id, "", CACHE_NULL_TTL, TimeUnit.MINUTES);
            return null;
        }
        //5.数据库存在，写入Redis缓存
        stringRedisTemplate.opsForValue().set(CACHE_SHOP_KEY + id, JSONUtil.toJsonStr(BeanUtil.beanToMap(shop))
                , RedisConstants.CACHE_SHOP_TTL, TimeUnit.MINUTES);
        //6.返回商铺信息
        return shop;
    }

    public Shop queryWithMutex(Long id) {

        //使用互斥锁来实现缓存击穿
        // 1.从Redis中查缓存
        String shopJson = stringRedisTemplate.opsForValue().get(RedisConstants.CACHE_SHOP_KEY + id);

        // 2.缓存命中
        if (shopJson != null) {
            if (shopJson.isEmpty()) {
                // 缓存的是空对象
                return null;
            }
            // 缓存的是店铺数据
            Shop shop = JSONUtil.toBean(shopJson, Shop.class);
            return shop;
        }

        try {
            //3.未命中，开始尝试获取互斥锁
            boolean lock = tryLock(LOCK_SHOP_KEY + id);
            //4.获取失败，进行休眠后重试
            if (!lock) {
                Thread.sleep(50);
                return queryWithMutex(id); //递归调用
            }
            //5.获取成功，查询数据库，写入Redis缓存
            Shop shop = getById(id);
            Thread.sleep(200); //模拟查询数据库的耗时
            //4.数据库不存在，返回错误
            if (shop == null) {
                stringRedisTemplate.opsForValue().set(CACHE_SHOP_KEY + id, "", CACHE_NULL_TTL, TimeUnit.MINUTES);
                return null;
            }
            //5.数据库存在，写入Redis缓存
            stringRedisTemplate.opsForValue().set(CACHE_SHOP_KEY + id, JSONUtil.toJsonStr(BeanUtil.beanToMap(shop))
                    , RedisConstants.CACHE_SHOP_TTL, TimeUnit.MINUTES);
            return shop;
        } catch (InterruptedException e) {
            throw new RuntimeException(e);
        } finally {
            //6.释放互斥锁
            unLock(LOCK_SHOP_KEY + id);
        }
    }
    // 添加逻辑过期字段
    public void saveShop2Redis(Long id, Long expireTime) throws InterruptedException {
        //1.查询店铺数据
        Shop shop = getById(id);
        Thread.sleep(200);
        //2.封装逻辑过期时间
        RedisData redisData = new RedisData();
        redisData.setData(shop);
        redisData.setExpireTime(LocalDateTime.now().plusMinutes(30)); //设置30分钟的逻辑过期时间
        //3.写入Redis缓存
        stringRedisTemplate.opsForValue().set(CACHE_SHOP_KEY + id, JSONUtil.toJsonStr(redisData), expireTime, TimeUnit.SECONDS);
    }

    private void unLock(String key) {
        //删除锁
        stringRedisTemplate.delete(key);
    }

    @Override
    @Transactional
    public Result update(Shop shop) {
        //1.更新数据库
        boolean isSuccess = updateById(shop);
        if (!isSuccess) {
            return Result.fail("更新失败");
        }
        if (shop.getId() == null) {
            return Result.fail("店铺不存在");
        }
        //2.删除Redis缓存
        stringRedisTemplate.delete(CACHE_SHOP_KEY + shop.getId());
        //3.返回成功
        return Result.ok();
    }

    private static final ExecutorService CACHE_REBUILD_EXECUTOR = Executors.newFixedThreadPool(10);//创建线程池
    // 使用逻辑过期来解决缓存击穿
    public Shop queryWithLogicExpire(Long id) {
        // 1.从Redis中查缓存
        String shopJson = stringRedisTemplate.opsForValue().get(RedisConstants.CACHE_SHOP_KEY + id);

        if(StrUtil.isBlank(shopJson)){//缓存未命中，返回null
            Shop shop = getById(id);
            if (shop == null) {
                return null; // 数据库中也没有数据
            }

            // 数据库中有数据，构建带逻辑过期时间的缓存
            RedisData redisData = new RedisData();
            redisData.setData(shop);
            redisData.setExpireTime(LocalDateTime.now().plusMinutes(30)); //设置30分钟的逻辑过期时间

            // 写入Redis缓存
            stringRedisTemplate.opsForValue().set(
                    CACHE_SHOP_KEY + id,
                    JSONUtil.toJsonStr(redisData),
                    20L, // 这个参数在逻辑过期方案中其实用处不大
                    TimeUnit.SECONDS
            );

            return shop;
        }
        // 2.缓存命中->判断过期时间
        LocalDateTime now = LocalDateTime.now();
        RedisData redisData = JSONUtil.toBean(shopJson, RedisData.class);
        JSONObject data = (JSONObject) redisData.getData();//获取data字段
        Shop shop = JSONUtil.toBean(data, Shop.class);//将data转换为Shop对象
        LocalDateTime expireTime = redisData.getExpireTime();
        //3.如果缓存未过期，直接返回
        if(expireTime.isAfter(now))return shop;
        //4.缓存已过期
        //4.1.获取互斥锁
        boolean lock = tryLock(LOCK_SHOP_KEY + id);
        //4.2.判断是否获取锁成功
        //4.2.1.成功，开启独立线程，实现缓存重建
        if(lock == true){
            //获取锁之后还需要进行二次检验，因为可能再获取的时间内进行了更新操作,毕竟有时间的流失虽然很少
            if(expireTime.isAfter(now))return shop;
            //这里最好还是使用线程池来执行任务，避免创建过多线程
            //new Thread(() -> {
            //    try {
            //        //重建缓存
            //        this.saveShop2Redis(id,20L);
            //    } catch (Exception e) {
            //        throw new RuntimeException(e);
            //    } finally {
            //        //释放锁
            //        unLock(LOCK_SHOP_KEY + id);
            //    }
            //}).start();
            //使用线程池来执行任务
            CACHE_REBUILD_EXECUTOR.submit(() -> {
                try {
                    this.saveShop2Redis(id,20L);
                }catch (Exception e){
                    throw new RuntimeException(e);
                }finally {
                    unLock(LOCK_SHOP_KEY + id);
                }
            });
        }
        //4.2.2.失败，直接返回旧数据
        return shop;
    }
}
