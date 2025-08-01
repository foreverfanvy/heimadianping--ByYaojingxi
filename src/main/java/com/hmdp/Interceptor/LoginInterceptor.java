package com.hmdp.Interceptor;

import com.hmdp.dto.UserDTO;
import com.hmdp.entity.User;
import com.hmdp.utils.UserHolder;
import org.springframework.web.servlet.HandlerInterceptor;

import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import javax.servlet.http.HttpSession;

public class LoginInterceptor implements HandlerInterceptor {

    @Override
    public boolean preHandle(HttpServletRequest request, HttpServletResponse response, Object handler) throws Exception {
        //1.获取Session
        HttpSession session = request.getSession();
        //2.获取用户
        Object user =  session.getAttribute("user");
        if (user == null) {
            //3.判断用户为空，返回401
            response.setStatus(401);
            return false;
        }
        //4.存在，保存用户信息到ThreadLocal
        //ThreadLocal<User> data = new ThreadLocal<>();
        //data.set(user);
        UserHolder.saveUser((UserDTO)user);
        //5.放行
        return true;
    }
    @Override
    public void afterCompletion(HttpServletRequest request, HttpServletResponse response, Object handler, Exception ex) throws Exception {

    }
}
