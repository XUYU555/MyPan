package com.xxyy.config;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import lombok.Getter;

import java.io.Serializable;

/**
 * @author xy
 * @date 2024-09-18 14:32
 */

@Getter
@JsonIgnoreProperties(ignoreUnknown = true)
public class SysSettingConfig implements Serializable {

    private static final long serialVersionUID = 1L;

    private final String sendMailTitle = "MyPan: 邮箱验证码";

    private final String sendMailText = "感谢您使用MyPan软件，你的邮箱注册验证码为：%s，有效期：5分钟";

    private final Long userInitSpace = 10L;

}
