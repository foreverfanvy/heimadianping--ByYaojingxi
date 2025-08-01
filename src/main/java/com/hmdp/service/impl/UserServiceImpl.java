package com.hmdp.service.impl;

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
import org.springframework.stereotype.Service;

import javax.servlet.http.HttpSession;

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

    private final UserMapper userMapper;

    public UserServiceImpl(UserMapper userMapper) {
        this.userMapper = userMapper;
    }

    @Override
    public Result sendCode(String phone, HttpSession session) {
        //1.校验手机号，不符合返回错误信息
        if (RegexUtils.isPhoneInvalid(phone)){
            return Result.fail("手机号格式错误");
        }
        //2.生成验证码_六位验证码
        String code = RandomUtil.randomNumbers(6);
        //3.保存验证码到session
        session.setAttribute("code",code);
        //4.发送验证码——需要集成短信服务，第三方API——这里模拟一下算了
        log.debug("发送验证码成功，验证码：{}",code);
        return Result.ok("发送成功");
    }

    @Override
    public Result login(LoginFormDTO loginForm, HttpSession session) {
        String phone = loginForm.getPhone();
        if (RegexUtils.isPhoneInvalid(phone)){
            return Result.fail("手机号格式错误");
        }
        //1.校验验证码,不一致直接报错
        String code = (String) session.getAttribute("code");
        String code2 = loginForm.getCode();
        if(code == null || !code2.equals(code)){
            return Result.fail("验证码错误");
        }
        //2.一致则根据手机号查询用户
        User user = query().eq("phone", phone).one();
        //3.不存在，则创建用户保存到数据库
        if(user == null){
            user = createUserwithPhone(phone);
        }
        //4.存在保存用户到Session中
        UserDTO userDTO = new UserDTO();
        BeanUtils.copyProperties(user,userDTO);
        session.setAttribute("user", userDTO);
        //5.返回结果
        return Result.ok(userDTO);
    }

    private User createUserwithPhone(String phone) {
        User user = new User();
        user.setPhone(phone);
        // 设置默认昵称这个是都会生成的，比如美团的手机号注册就是这样的
        user.setNickName("user_"+RandomUtil.randomString(10));
        // 保存用户到数据库
        save(user);
        // 返回用户
        return user;
    }
}
