package com.xxyy.controller;

import cn.hutool.core.bean.BeanUtil;
import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.xxyy.annotation.GlobalInterceptor;
import com.xxyy.annotation.VerifyParams;
import com.xxyy.dto.EmailLoginDTO;
import com.xxyy.dto.EmailRegisterDTO;
import com.xxyy.dto.LoginInfoVO;
import com.xxyy.dto.UserSpaceVO;
import com.xxyy.entity.UserInfo;
import com.xxyy.entity.enums.RegexPattern;
import com.xxyy.entity.enums.ResponseCodeEnums;
import com.xxyy.service.IUserInfoService;
import com.xxyy.utils.StringTools;
import com.xxyy.utils.common.AppException;
import com.xxyy.utils.CreateImageCode;
import com.xxyy.utils.RedisConstants;
import com.xxyy.utils.common.Result;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import javax.servlet.http.HttpSession;
import java.io.IOException;
import java.util.Map;
import java.util.concurrent.TimeUnit;

/**
 * @author xy
 * @date 2024-09-17 17:46
 */

@RestController(value = "userRestController")
public class UserInfoController {

    @Autowired
    StringRedisTemplate stringRedisTemplate;

    @Autowired
    IUserInfoService userService;


    @GetMapping(value = "/checkCode")
    public void checkCode(HttpServletResponse response,HttpSession session
            , @RequestParam("type") Integer type) throws IOException {
        //创建一个 CreateImageCode 的实例，用于生成验证码图片
        CreateImageCode vCode = new CreateImageCode(130, 38, 5, 10);
        //设置请求头信息
        response.setHeader("Pragma", "no-cache");
        response.setHeader("Cache-Control", "no-cache");
        response.setDateHeader("Expires", 0);
        //设置响应内容类型为JPEG格式的图片
        response.setContentType("image/jpeg");
        //生成数字
        String code = vCode.getCode();
        // 0代表登录注册，其他代表邮箱验证码发送
        if (type == null || type == 0) {
            //session中设置code
            //session.setAttribute(CodeConstants.CHECK_CODE_KEY, code);
            stringRedisTemplate.opsForValue().set(RedisConstants.MYPAN_LOGIN_CODE + session.getId(), code
                    , 5, TimeUnit.MINUTES);
        } else {
            // session.setAttribute(CodeConstants.CHECK_CODE_KEY_EMAIL, code);
            stringRedisTemplate.opsForValue().set(RedisConstants.MYPAN_LOGIN_CODE_EMAIL + session.getId(), code
                    , 5, TimeUnit.MINUTES);
        }
        //生成的验证码图片写入到 HTTP 响应的输出流中
        vCode.write(response.getOutputStream());
    }

    @PostMapping(value = "/sendEmailCode")
    @GlobalInterceptor(checkParams = true, checkLoing = false)
    public Result<?> sendEmailCode(@ModelAttribute @VerifyParams EmailLoginDTO emailLoginDTO, HttpSession session) {
        try {
            String iCode = stringRedisTemplate.opsForValue().get(RedisConstants.MYPAN_LOGIN_CODE_EMAIL + session.getId());
            if (!emailLoginDTO.getCheckCode().equalsIgnoreCase(iCode)) {
                throw new AppException("图像验证码不正确");
            }
            return userService.sendEmailCode(emailLoginDTO);
        } finally {
            stringRedisTemplate.delete(RedisConstants.MYPAN_LOGIN_CODE_EMAIL + session.getId());
        }
    }

    @PostMapping(value = "/register")
    @GlobalInterceptor(checkParams = true, checkLoing = false)
    public Result<?> register(@ModelAttribute @VerifyParams EmailRegisterDTO emailRegisterDTO, HttpSession session) {
        try {
            String iCode = stringRedisTemplate.opsForValue().get(RedisConstants.MYPAN_LOGIN_CODE + session.getId());
            if (!emailRegisterDTO.getCheckCode().equalsIgnoreCase(iCode)) {
                throw new AppException("图像验证码不正确");
            }
            return userService.register(emailRegisterDTO);
        } finally {
            stringRedisTemplate.delete(RedisConstants.MYPAN_LOGIN_CODE + session.getId());
        }
    }

    @PostMapping(value = "/login")
    @GlobalInterceptor(checkParams = true, checkLoing = false)
    public Result<LoginInfoVO> login(HttpSession session, @VerifyParams(required = true) String email,
                                     @VerifyParams(required = true)String password,
                                     @VerifyParams(required = true)String checkCode) {
        try {
            String iCode = stringRedisTemplate.opsForValue().get(RedisConstants.MYPAN_LOGIN_CODE + session.getId());
            if (!checkCode.equals(iCode)) {
                throw new AppException("图像验证码不正确");
            }
            return Result.data(userService.login(session, email, password));
        } finally {
            stringRedisTemplate.delete(RedisConstants.MYPAN_LOGIN_CODE + session.getId());
        }
    }

    @PostMapping(value = "/resetPwd")
    @GlobalInterceptor(checkParams = true, checkLoing = false)
    public Result<?> resetPwd(HttpSession session, @VerifyParams(required = true, regex = RegexPattern.EMAIL) String email,
                              @VerifyParams(required = true, regex = RegexPattern.PASSWORD)String password,
                              @VerifyParams(required = true) String checkCode,
                              @VerifyParams(required = true) String emailCode) {
        try {
            String iCode = stringRedisTemplate.opsForValue().get(RedisConstants.MYPAN_LOGIN_CODE + session.getId());
            if (!checkCode.equals(iCode)) {
                throw new AppException("图像验证码不正确");
            }
            userService.resetPwd(email, password, emailCode);
            return Result.ok();
        } finally {
            stringRedisTemplate.delete(RedisConstants.MYPAN_LOGIN_CODE + session.getId());
        }
    }

    @GetMapping(value = "/getAvatar/{userId}")
    @GlobalInterceptor(checkParams = true, checkLoing = false)
    public void getAvatar(@PathVariable(value = "userId") @VerifyParams(required = true) String userId,
                               HttpServletResponse response) {
        userService.getUserAvatar(userId, response);
    }

    @PostMapping(value = "/getUseSpace")
    @GlobalInterceptor
    public Result<UserSpaceVO> getUseSpace(HttpServletRequest request) {
        return userService.getUseSpace(request.getHeader("authorization"));
    }

    @PostMapping(value = "/logout")
    @GlobalInterceptor
    public Result<?> logout(HttpServletRequest request) {
        userService.logout(request.getHeader("authorization"));
        return Result.ok();
    }

    @PostMapping(value = "/updatePassword")
    @GlobalInterceptor(checkParams = true)
    public Result<?> updatePwd(@VerifyParams(required = true, regex = RegexPattern.PASSWORD) String password, HttpServletRequest request) {
        String token = request.getHeader("authorization");
        Map<Object, Object> entries = stringRedisTemplate.opsForHash().entries(RedisConstants.MYPAN_LOGIN_USER_KEY + token);
        if (entries.isEmpty()) {
            throw new AppException(ResponseCodeEnums.CODE_901);
        }
        UserInfo userInfo = userService.getOne(new QueryWrapper<UserInfo>().eq("user_id", entries.get("userId")));
        userInfo.setPassword(StringTools.byMd5(password));
        userService.updateById(userInfo);
        return Result.ok();
    }

    @PostMapping(value = "/updateUserAvatar")
    @GlobalInterceptor(checkParams = true)
    public Result<?> updateUserAvatar(@VerifyParams(required = true) MultipartFile avatar, HttpServletRequest request) throws IOException {
        userService.updateAvatar(avatar, request.getHeader("authorization"));
        return Result.ok();
    }

    @PostMapping(value = "/getUserInfo")
    public Result<LoginInfoVO> getUserInfo(HttpServletRequest request) {
        String token = request.getHeader("authorization");
        Map<Object, Object> userMap = stringRedisTemplate.opsForHash().entries(RedisConstants.MYPAN_LOGIN_USER_KEY + token);
        LoginInfoVO loginInfoVO = BeanUtil.fillBeanWithMap(userMap, new LoginInfoVO(), true);
        return Result.data(loginInfoVO);
    }

}
