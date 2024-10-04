package com.xxyy.service.impl;

import com.alibaba.fastjson2.JSON;
import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.xxyy.entity.FileInfo;
import com.xxyy.entity.enums.FileDelFlagEnums;
import com.xxyy.entity.enums.FileStatusEnums;
import com.xxyy.entity.enums.FolderTypeEnums;
import com.xxyy.entity.vo.PagingQueryVO;
import com.xxyy.entity.vo.UserSpaceVO;
import com.xxyy.mapper.FileInfoMapper;
import com.xxyy.mapper.UserInfoMapper;
import com.xxyy.service.IRecycleBinService;
import com.xxyy.utils.CodeConstants;
import com.xxyy.utils.RedisConstants;
import com.xxyy.utils.StringTools;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import javax.annotation.Resource;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * @author xy
 * @date 2024-10-02 20:46
 */

@Service
public class RecycleBinServiceImpl extends ServiceImpl<FileInfoMapper, FileInfo> implements IRecycleBinService {

    @Autowired
    StringRedisTemplate stringRedisTemplate;

    @Resource
    UserInfoMapper userInfoMapper;

    @Override
    public PagingQueryVO getRecycleList(Page<FileInfo> fileInfoPage, String token) {
        String userId = (String)stringRedisTemplate.opsForHash().entries(RedisConstants.MYPAN_LOGIN_USER_KEY + token).get("userId");
        // 添加查询条件
        QueryWrapper<FileInfo> fileQueryWrapper = new QueryWrapper<>();
        fileQueryWrapper.eq("user_id", userId);
        fileQueryWrapper.eq("del_flag", FileDelFlagEnums.RECOVERY.getCode());
        fileQueryWrapper.orderByDesc("recovery_time");
        // 使用page方法进行分页查询
        Page<FileInfo> pageResult = page(fileInfoPage, fileQueryWrapper);
        return PagingQueryVO.of(pageResult);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void recoverFile(String token, String fileIds) {
        String userId = (String)stringRedisTemplate.opsForHash().entries(RedisConstants.MYPAN_LOGIN_USER_KEY + token).get("userId");
        List<String> ids = Arrays.stream(fileIds.split(",")).collect(Collectors.toList());
        // 获得回收站中的文件
        List<FileInfo> fileList = getBaseMapper().findRecycleFileList(userId, ids, FolderTypeEnums.DOCUMENT);
        // 获得回收站中的目录并先恢复目录
        List<FileInfo> folderList = getBaseMapper().findRecycleFileList(userId, ids, FolderTypeEnums.FOLDER)
                .stream().peek(this::checkParentFileExist).collect(Collectors.toList());
        List<FileInfo> list = new ArrayList<>();
        // 在将目录下的文件还原
        for (FileInfo fileInfo : folderList) {
            recoverFolderAndSubFile(fileInfo, list);
        }
        List<FileInfo> newFileList = fileList.stream().peek(this::checkParentFileExist).collect(Collectors.toList());
        newFileList.addAll(list);
        updateBatchById(newFileList);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void deleteFile(String token, String fileIds) {
        String userId = (String)stringRedisTemplate.opsForHash().entries(RedisConstants.MYPAN_LOGIN_USER_KEY + token).get("userId");
        List<String> ids = Arrays.stream(fileIds.split(",")).collect(Collectors.toList());
        List<FileInfo> recycleFileList = getBaseMapper().findRecycleFileList(userId, ids, FolderTypeEnums.ALL);
        // 获得所选的文件与目录下的文件
        List<FileInfo> subFileList = new ArrayList<>();
        for (FileInfo fileInfo : recycleFileList) {
            recoverFolderAndSubFile(fileInfo, subFileList);
        }
        long allSize = 0L;
        for (FileInfo fileInfo : subFileList) {
            if (fileInfo.getFolderType().intValue() != FolderTypeEnums.FOLDER.getType().intValue()) {
                allSize += fileInfo.getFileSize();
            }
        }
        // 修改数据库数据
        userInfoMapper.updateUserSpace(userId, -allSize, null);
        // 更新缓存
        String json = stringRedisTemplate.opsForValue().get(RedisConstants.MYPAN_LOGIN_USER_SPACE + userId);
        UserSpaceVO userSpaceVO = JSON.parseObject(json, UserSpaceVO.class);
        userSpaceVO.setUseSpace(userSpaceVO.getUseSpace() - allSize);
        stringRedisTemplate.opsForValue().set(RedisConstants.MYPAN_LOGIN_USER_SPACE + userId, JSON.toJSONString(userSpaceVO));
        // 删除文件
        removeByIds(subFileList.stream().map(FileInfo::getFileId).collect(Collectors.toList()));
    }

    private void checkParentFileExist(FileInfo fileInfo) {
        FileInfo one = query().eq("file_id", fileInfo.getFilePid()).eq("user_id", fileInfo.getUserId())
                .eq("status", FileStatusEnums.USING.getCode())
                .eq("del_flag", FileDelFlagEnums.NORMAL.getCode()).one();
        if (one == null) {
            // 放入根目录
            fileInfo.setFilePid(CodeConstants.ZERO_STR);
        }
        // 查看目录名是否重复
        if (checkFileName(fileInfo)) {
            fileInfo.setFileName(fileInfo.getFileName() + "_" + StringTools.getRandomString(CodeConstants.LENGTH_5));
        }
        fileInfo.setRecoveryTime(null);
        fileInfo.setDelFlag(FileDelFlagEnums.NORMAL.getCode());
    }

    private void recoverFolderAndSubFile(FileInfo fileInfo, List<FileInfo> subFileList) {
        if (fileInfo.getFolderType().intValue() != FolderTypeEnums.DOCUMENT.getType()) {
            List<FileInfo> list = query().eq("file_pid", fileInfo.getFileId())
                    .eq("user_id", fileInfo.getUserId()).eq("del_flag", FileDelFlagEnums.DELETE.getCode()).list();
            for (FileInfo info : list) {
                recoverFolderAndSubFile(info, subFileList);
            }
        }
        fileInfo.setRecoveryTime(null);
        fileInfo.setDelFlag(FileDelFlagEnums.NORMAL.getCode());
        subFileList.add(fileInfo);
    }

    private boolean checkFileName(FileInfo fileInfo) {
        FileInfo one = query().eq("file_pid", fileInfo.getFilePid()).eq("user_id", fileInfo.getUserId())
                .eq("file_name", fileInfo.getFileName())
                .eq("status", FileStatusEnums.USING.getCode())
                .eq("del_flag", FileDelFlagEnums.NORMAL.getCode()).one();
        return one != null;
    }
}
