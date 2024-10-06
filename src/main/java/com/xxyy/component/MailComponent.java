package com.xxyy.component;

import com.alibaba.fastjson.JSON;
import com.xxyy.entity.dto.SysSettingDTO;
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

    public SysSettingDTO getSysSetting() {
        String setting = stringRedisTemplate.opsForValue().get(RedisConstants.MYPAN_SYSTEM_MAIL);
        if (setting == null || setting.isEmpty()) {
            SysSettingDTO sysSettingDTO = new SysSettingDTO();
            stringRedisTemplate.opsForValue().set(RedisConstants.MYPAN_SYSTEM_MAIL, JSON.toJSONString(sysSettingDTO));
            return sysSettingDTO;
        }
        return JSON.parseObject(setting, SysSettingDTO.class);
    }

    public void saveSysSetting(SysSettingDTO sysSettingDTO) {
        String jsonString = JSON.toJSONString(sysSettingDTO);
        stringRedisTemplate.opsForValue().set(RedisConstants.MYPAN_SYSTEM_MAIL, jsonString);
    }

}
