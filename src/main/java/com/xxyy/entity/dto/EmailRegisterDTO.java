package com.xxyy.entity.dto;

import com.xxyy.annotation.VerifyParams;
import com.xxyy.entity.enums.RegexPattern;
import lombok.Data;

/**
 * @author xy
 * @date 2024-09-19 15:43
 */

@Data
public class EmailRegisterDTO {

    // 电子邮箱
    @VerifyParams(regex = RegexPattern.EMAIL, max = 150, required = true)
    private String email;

    // 邮箱验证码
    @VerifyParams(required = true)
    private String emailCode;

    // 昵称
    @VerifyParams(required = true, regex = RegexPattern.NICKNAME)
    private String nickName;

    // 密码
    @VerifyParams(required = true, regex = RegexPattern.PASSWORD)
    private String password;

    // 图片验证码
    @VerifyParams(required = true)
    private String checkCode;
}
