package com.xxyy.service;

import com.baomidou.mybatisplus.extension.service.IService;
import com.xxyy.entity.dto.EmailLoginDTO;
import com.xxyy.entity.dto.EmailRegisterDTO;
import com.xxyy.entity.vo.LoginInfoVO;
import com.xxyy.entity.vo.UserSpaceVO;
import com.xxyy.entity.UserInfo;
import com.xxyy.utils.common.Result;
import org.springframework.web.multipart.MultipartFile;

import javax.servlet.http.HttpServletResponse;
import javax.servlet.http.HttpSession;
import java.io.IOException;

/**
 * @author xy
 * @date 2024-09-17 21:27
 */


public interface IUserInfoService extends IService<UserInfo> {

    Result<?> sendEmailCode(EmailLoginDTO emailLoginDTO);

    Result<?> register(EmailRegisterDTO emailRegisterDTO);

    LoginInfoVO login(HttpSession session, String email, String password);

    void resetPwd(String email, String password, String emailCode);

    void getUserAvatar(String userId, HttpServletResponse response);

    Result<UserSpaceVO> getUseSpace(String token);

    void logout(String token);

    void updateAvatar(MultipartFile avatarFile, String token) throws IOException;
}
