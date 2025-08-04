package com.hmdp.controller;


import cn.hutool.json.JSONObject;
import cn.hutool.json.JSONUtil;
import com.hmdp.dto.Result;
import com.hmdp.entity.ShopType;
import com.hmdp.service.IShopTypeService;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import javax.annotation.Resource;
import java.util.List;

import static com.hmdp.utils.RedisConstants.CACHE_SHOP_TYPE_KEY;

/**
 * <p>
 * 前端控制器
 * </p>
 *
 * @author 虎哥
 * @since 2021-12-22
 */
@RestController
@RequestMapping("/shop-type")
public class ShopTypeController {
    @Resource
    private IShopTypeService typeService;
    @Resource
    private StringRedisTemplate stringRedisTemplate;

    @GetMapping("list")
    public Result queryTypeList() {
        // 从Redis获取缓存数据
        String listJson = stringRedisTemplate.opsForValue().get(CACHE_SHOP_TYPE_KEY);

        // 如果缓存中存在数据，则直接返回
        if (listJson != null && !listJson.isEmpty()) {
            List<ShopType> cachedList = JSONUtil.toList(JSONUtil.parseArray(listJson), ShopType.class);
            return Result.ok(cachedList);
        }

        // 缓存不存在，从数据库查询
        List<ShopType> typeList = typeService.query().orderByAsc("sort").list();

        // 将查询结果存入Redis缓存
        stringRedisTemplate.opsForValue().set(CACHE_SHOP_TYPE_KEY, JSONUtil.toJsonStr(typeList));

        return Result.ok(typeList);
    }

}
