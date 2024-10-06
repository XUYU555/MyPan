package com.xxyy.entity.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.xxyy.annotation.VerifyParams;
import lombok.Getter;

import java.io.Serializable;

/**
 * @author xy
 * @date 2024-09-18 14:32
 */

@Getter
@JsonIgnoreProperties(ignoreUnknown = true)
public class SysSettingDTO implements Serializable {

    private static final long serialVersionUID = 1L;

    @VerifyParams(required = true)
    private final String registerEmailTitle = "MyPan: 邮箱验证码";

    @VerifyParams(required = true)
    private final String registerEmailContent = "感谢您使用MyPan软件，你的邮箱注册验证码为：%s，有效期：5分钟";

    @VerifyParams(required = true)
    private final Long userInitUseSpace = 10L;

}
