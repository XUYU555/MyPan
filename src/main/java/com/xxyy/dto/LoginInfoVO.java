package com.xxyy.dto;

import com.xxyy.entity.UserInfo;
import lombok.Data;

/**
 * @author xy
 * @date 2024-09-19 22:53
 */

@Data
public class LoginInfoVO {

    private String nickName;

    private String userId;

    private String avatar;

    private Boolean admin;

    private String token;

    public static LoginInfoVO of(UserInfo userInfo, String token) {
        LoginInfoVO loginInfoVO = new LoginInfoVO();
        loginInfoVO.userId = userInfo.getUserId();
        loginInfoVO.nickName = userInfo.getNickName();
        loginInfoVO.avatar = userInfo.getAvatar();
        loginInfoVO.token = token;
        return loginInfoVO;
    }

}
