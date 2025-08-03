package com.hmdp.service.impl;

import cn.hutool.core.bean.BeanUtil;
import cn.hutool.core.bean.copier.CopyOptions;
import cn.hutool.core.util.RandomUtil;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.hmdp.dto.LoginFormDTO;
import com.hmdp.dto.Result;
import com.hmdp.dto.UserDTO;
import com.hmdp.entity.User;
import com.hmdp.mapper.UserMapper;
import com.hmdp.service.IUserService;
import com.hmdp.utils.RegexUtils;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.BeanUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

import javax.servlet.http.HttpSession;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
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
@Slf4j
public class UserServiceImpl extends ServiceImpl<UserMapper, User> implements IUserService {

    @Autowired
    private StringRedisTemplate stringRedisTemplate;
    @Autowired
    private RedisTemplate redisTemplate;


    @Override
    public Result sendCode(String phone, HttpSession session) {
        //1.校验手机号，不符合返回错误信息
        if (RegexUtils.isPhoneInvalid(phone)) {
            return Result.fail("手机号格式错误");
        }
        //2.生成验证码_六位验证码
        String code = RandomUtil.randomNumbers(6);
        //3.保存验证码到redis,设置验证码有效期，这里设置5分钟
        stringRedisTemplate.opsForValue().set(LOGIN_CODE_KEY + phone, code, LOGIN_CODE_TTL, TimeUnit.MINUTES);
        //4.发送验证码——需要集成短信服务，第三方API——这里模拟一下算了
        log.debug("发送验证码成功，验证码：{}", code);
        return Result.ok("发送成功");
    }

    @Override
    public Result login(LoginFormDTO loginForm, HttpSession session) {
        //1.校验手机号，不符合返回错误信息
        String phone = loginForm.getPhone();
        if (RegexUtils.isPhoneInvalid(phone)) {
            return Result.fail("手机号格式错误");
        }
        //2.从Redis中获取验证码
        String cachecode = stringRedisTemplate.opsForValue().get(LOGIN_CODE_KEY + phone);
        String code = loginForm.getCode();
        if (cachecode == null || !cachecode.equals(code)) {
            return Result.fail("验证码错误");
        }
        //3.查询用户
        User user = query().eq("phone", phone).one();
        //4.不存在，则创建用户保存到数据库
        if (user == null) {
            user = createUserwithPhone(phone);
        }
        //5.存在保存用户到Redis中
        //5.1.声明一个Token令牌
        String token = UUID.randomUUID().toString();
        //5.2.将User转成对应的Hash存入Redis
        UserDTO userDTO = new UserDTO();
        BeanUtils.copyProperties(user, UserDTO.class);
        //5.3.将UserDTO转成Map保存到Redis中
        Map<String, Object> usermap = BeanUtil.beanToMap(userDTO,new HashMap<>(),
                        CopyOptions.create().setIgnoreNullValue(true)
                        .setFieldValueEditor((fieldName, fieldValue) -> fieldValue.toString()
                        ));//这里是一个易错点！！！
        stringRedisTemplate.opsForHash().putAll(LOGIN_USER_KEY+token,usermap);
        //5.4.设置Token的有效期，但是后续只要访问了拦截器就会自动更新设置为30分钟从而实现现代web网站的登录效果
        stringRedisTemplate.expire(LOGIN_USER_KEY+token,LOGIN_USER_TTL,TimeUnit.MINUTES);
        //6.返回Token
        return Result.ok(userDTO);
    }

    private User createUserwithPhone(String phone) {
        User user = new User();
        user.setPhone(phone);
        // 设置默认昵称这个是都会生成的，比如美团的手机号注册就是这样的
        user.setNickName("user_" + RandomUtil.randomString(10));
        // 保存用户到数据库
        save(user);
        // 返回用户
        return user;
    }
}
