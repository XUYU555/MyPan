package com.xxyy.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.xxyy.entity.FileInfo;
import org.apache.ibatis.annotations.Param;

/**
 * @author xy
 * @date 2024-09-22 11:06
 */
public interface FileInfoMapper extends BaseMapper<FileInfo> {

    Long selectUseSpace(@Param(value = "userId") String userId);

}
