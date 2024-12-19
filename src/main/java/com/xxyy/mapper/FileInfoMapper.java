package com.xxyy.mapper;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.baomidou.mybatisplus.extension.conditions.query.QueryChainWrapper;
import com.baomidou.mybatisplus.extension.toolkit.ChainWrappers;
import com.github.yulichang.base.MPJBaseMapper;
import com.github.yulichang.interfaces.MPJBaseJoin;
import com.xxyy.entity.FileInfo;
import com.xxyy.entity.enums.FileDelFlagEnums;
import com.xxyy.entity.enums.FileStatusEnums;
import com.xxyy.entity.enums.FolderTypeEnums;
import org.apache.ibatis.annotations.Param;

import java.util.List;

/**
 * @author xy
 * @date 2024-09-22 11:06
 */
public interface FileInfoMapper extends MPJBaseMapper<FileInfo> {

    Long selectUseSpace(@Param(value = "userId") String userId);

     default List<FileInfo> findRecycleFileList(String userId, List<String> ids, FolderTypeEnums folderTypeEnums) {
        QueryChainWrapper<FileInfo> query = ChainWrappers.queryChain(this);
        return query.eq("user_id", userId).in("file_id", ids)
                .eq("del_flag", FileDelFlagEnums.RECOVERY.getCode())
                .eq(folderTypeEnums != FolderTypeEnums.ALL, "folder_type", folderTypeEnums.getType())
                .in("status", FileStatusEnums.USING.getCode(), FileStatusEnums.TRANSFER_FAIL.getCode())
                .list();
    }


    default List<FileInfo> findUsingFileList(String userId, List<String> ids, FolderTypeEnums folderTypeEnums) {
        QueryChainWrapper<FileInfo> query = ChainWrappers.queryChain(this);
        return query.eq("user_id", userId).in("file_id", ids)
                .eq("folder_type", folderTypeEnums.getType())
                .in("status", FileStatusEnums.USING.getCode(), FileStatusEnums.TRANSFER_FAIL.getCode())
                .eq("del_flag", FileDelFlagEnums.NORMAL.getCode()).list();
    }


}
