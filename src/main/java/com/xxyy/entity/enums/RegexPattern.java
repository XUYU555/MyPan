package com.xxyy.entity.enums;

/**
 * @author xy
 * @date 2024-09-18 21:22
 */

public enum RegexPattern {

    // 不进行校验
    NO(".*"),
    // IP 地址校验（IPv4）
    IP_ADDRESS("^((25[0-5]|2[0-4][0-9]|[01]?[0-9][0-9]?)\\.){3}(25[0-5]|2[0-4][0-9]|[01]?[0-9][0-9]?)$"),
    // 正整数校验
    POSITIVE_INTEGER("^[1-9]\\d*$"),
    // 由数字、26个字母或者下划线组成的字符串
    PASSWORD("^[A-Za-z0-9_]+$"),
    // 邮箱校验
    EMAIL("^[a-zA-Z0-9_.+-]+@[a-zA-Z0-9-]+\\.[a-zA-Z0-9-.]+$"),
    // 手机号码校验（中国大陆）
    PHONE("^((\\+?86)|((\\+?1)))?1[3-9]\\d{9}$"),
    // 数字、字母、中文、下划线组合的字符串
    ALPHANUMERIC_CHINESE_UNDERSCORE("^[\\u4e00-\\u9fa5A-Za-z0-9_]+$"),
    // 只能是字母、数字、特殊字符（8-18位）
    NICKNAME("^(?=.*[A-Za-z])(?=.*\\d)(?=.*[@$!%*#?&])[A-Za-z\\d@$!%*#?&]{8,18}$");


    // 正则表达式
    private final String pattern;

    // 构造函数
    RegexPattern(String pattern) {
        this.pattern = pattern;
    }

    // 获取正则表达式
    public String getPattern() {
        return pattern;
    }

    // 校验输入值是否匹配正则表达式
    public boolean validate(String input) {
        return input != null && input.matches(this.pattern);
    }
}