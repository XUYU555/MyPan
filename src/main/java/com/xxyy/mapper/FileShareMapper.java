package com.xxyy.mapper;


import com.github.yulichang.base.MPJBaseMapper;
import com.xxyy.entity.FileShare;
import org.apache.ibatis.annotations.Param;

/**
 * @author xy
 * @date 2024-10-05 19:04
 */


public interface FileShareMapper extends MPJBaseMapper<FileShare> {

    void increaseShowCount(@Param(value = "shareId") String shareId);
}
