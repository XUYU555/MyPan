package com.xxyy.component;

import com.alibaba.fastjson.JSON;
import com.xxyy.config.SysSettingConfig;
import com.xxyy.utils.RedisConstants;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;

/**
 * @author xy
 * @date 2024-09-18 14:38
 */

@Component
public class MailComponent {

    @Autowired
    StringRedisTemplate stringRedisTemplate;

    public SysSettingConfig getSysSetting() {
        String setting = stringRedisTemplate.opsForValue().get(RedisConstants.MYPAN_SYSTEM_MAIL);
        if (setting == null || setting.isEmpty()) {
            SysSettingConfig sysSettingConfig = new SysSettingConfig();
            stringRedisTemplate.opsForValue().set(RedisConstants.MYPAN_SYSTEM_MAIL, JSON.toJSONString(sysSettingConfig));
            return sysSettingConfig;
        }
        return JSON.parseObject(setting, SysSettingConfig.class);
    }

}
