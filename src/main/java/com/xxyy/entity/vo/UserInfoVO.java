package com.xxyy.entity.vo;

import com.xxyy.entity.UserInfo;
import lombok.Data;

import java.text.SimpleDateFormat;

/**
 * @author xy
 * @date 2024-10-06 14:11
 */

@Data
public class UserInfoVO {

    private String userId;

    private String nickName;

    private String qqAvatar;

    private String joinTime;

    private String lastLoginTime;

    private Integer status;

    private Long useSpace;

    private Long totalSpace;

    public static UserInfoVO of(UserInfo userInfo) {
        SimpleDateFormat simpleDateFormat = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss");
        UserInfoVO userInfoVO = new UserInfoVO();
        userInfoVO.userId = userInfo.getUserId();
        userInfoVO.nickName = userInfo.getNickName();
        userInfoVO.qqAvatar = userInfo.getAvatar();
        userInfoVO.joinTime = simpleDateFormat.format(userInfo.getRegisterTime());
        userInfoVO.lastLoginTime = simpleDateFormat.format(userInfo.getLastLoginTime());
        userInfoVO.status = userInfo.getStatus();
        userInfoVO.useSpace = userInfo.getUseSpace();
        userInfoVO.totalSpace = userInfo.getTotalSpace();
        return userInfoVO;
    }
}
