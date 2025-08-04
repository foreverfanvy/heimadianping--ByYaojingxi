package com.hmdp.service.impl;

import cn.hutool.core.bean.BeanUtil;
import cn.hutool.json.JSONUtil;
import com.hmdp.dto.Result;
import com.hmdp.entity.Shop;
import com.hmdp.mapper.ShopMapper;
import com.hmdp.service.IShopService;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.hmdp.utils.RedisConstants;
import lombok.Builder;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

import javax.annotation.Resource;
import java.util.Map;

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
        //1.从Redis中查缓存
        Map<Object, Object> map = stringRedisTemplate.opsForHash().entries(RedisConstants.CACHE_SHOP_KEY + id);
        //2.命中直接返回信息
        if (!map.isEmpty()) {
            Shop shop = new Shop();
            BeanUtil.fillBeanWithMap(map, shop, false);
            return Result.ok(shop);
        }
        //3.不存在，查询数据库
        Shop shop = getById(id);
        //4.数据库不存在，返回错误
        if (shop == null) return Result.fail("店铺不存在");
        //5.数据库存在，写入Redis缓存
        stringRedisTemplate.opsForValue().set(RedisConstants.CACHE_SHOP_KEY + id, JSONUtil.toJsonStr(BeanUtil.beanToMap(shop)));
        //6.返回商铺信息
        return Result.ok(shop);
    }
}
