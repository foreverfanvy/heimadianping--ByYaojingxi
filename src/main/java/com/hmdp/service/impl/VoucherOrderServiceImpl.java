package com.hmdp.service.impl;

import com.hmdp.dto.Result;
import com.hmdp.dto.UserDTO;
import com.hmdp.entity.SeckillVoucher;
import com.hmdp.entity.VoucherOrder;
import com.hmdp.mapper.VoucherOrderMapper;
import com.hmdp.service.ISeckillVoucherService;
import com.hmdp.service.IVoucherOrderService;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.hmdp.utils.RedisIdWorker;
import com.hmdp.utils.UserHolder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import javax.annotation.Resource;
import java.time.LocalDateTime;

/**
 * <p>
 *  服务实现类
 * </p>
 *
 * @author 虎哥
 * @since 2021-12-22
 */
@Service
public class VoucherOrderServiceImpl extends ServiceImpl<VoucherOrderMapper, VoucherOrder> implements IVoucherOrderService {
    @Resource
    private ISeckillVoucherService seckillVoucherService;
    @Resource
    private RedisIdWorker redisIdWorker;

    @Override
    @Transactional
    public Result seckillVoucher(Long voucherId) {
        //1.查询优惠券
        SeckillVoucher voucher = seckillVoucherService.getById(voucherId);
        //2.判断秒杀是否在有效的时间段内
        if(!voucher.getBeginTime().isBefore(LocalDateTime.now())){
            return Result.fail("秒杀尚未开始");
        }
        if(!voucher.getEndTime().isAfter(LocalDateTime.now())){
            return Result.fail("秒杀尚未开始");
        }
        //3.判断库存是否充足
        if(voucher.getStock()<1){
            return Result.fail("库存不足");
        }
        //4.扣减库存
        boolean b = seckillVoucherService.update().setSql("stock = stock - 1")
                .eq("voucher_id", voucherId).gt("stock", 0)
                .gt("stock", 0) // 确保库存大于0
                .update();
        if (b == false) {
            // 扣减库存失败，可能是因为库存不足
            return Result.fail("库存不足");
        }
        //5.创建订单
        VoucherOrder voucherOrder = new VoucherOrder();
        // 设置订单属性
        long id = redisIdWorker.nextId("order");
        voucherOrder.setVoucherId(id);
        UserDTO user = UserHolder.getUser();
        voucherOrder.setUserId(user.getId());
        voucherOrder.setVoucherId(voucherId);
        save(voucherOrder);
        //6.返回订单id
        return Result.ok(voucherId);
    }
}
