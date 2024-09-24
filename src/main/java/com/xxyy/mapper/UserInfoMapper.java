package com.xxyy.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.xxyy.entity.UserInfo;
import org.apache.ibatis.annotations.Param;

/**
 * @author xy
 * @date 2024-09-17 17:45
 */

public interface UserInfoMapper extends BaseMapper<UserInfo> {

    void UpdateUserSpace(@Param(value = "userId") String userId,@Param(value = "useSpace") Long useSpace,@Param(value = "totalSpace") Long totalSpace);

}
