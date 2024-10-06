package com.xxyy.mapper;

import com.github.yulichang.base.MPJBaseMapper;
import com.xxyy.entity.UserInfo;
import org.apache.ibatis.annotations.Param;

/**
 * @author xy
 * @date 2024-09-17 17:45
 */

public interface UserInfoMapper extends MPJBaseMapper<UserInfo> {

    void updateUserSpace(@Param(value = "userId") String userId, @Param(value = "useSpace") Long useSpace, @Param(value = "totalSpace") Long totalSpace);

}
