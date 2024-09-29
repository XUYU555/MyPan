package com.xxyy.entity.dto;

import com.xxyy.annotation.VerifyParams;
import com.xxyy.entity.enums.RegexPattern;
import lombok.Data;

/**
 * @author xy
 * @date 2024-09-17 19:00
 */

@Data
public class EmailLoginDTO {

    /**
     *  电子邮箱
     */
    @VerifyParams(required = true, regex = RegexPattern.EMAIL)
    private String email;

    /**
    *   图片验证码
    */
    @VerifyParams(required = true)
    private String checkCode;

    /*
    * 表示是登录还是注册
    * 0：为注册
    * 1：为登录
    * */
    @VerifyParams(required = true)
    private Integer type;

}
