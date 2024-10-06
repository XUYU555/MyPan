package com.xxyy.service.impl;

import com.alibaba.fastjson2.JSON;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.github.yulichang.wrapper.MPJLambdaWrapper;
import com.xxyy.entity.FileInfo;
import com.xxyy.entity.UserInfo;
import com.xxyy.entity.dto.FileQueryDTO;
import com.xxyy.entity.enums.ResponseCodeEnums;
import com.xxyy.entity.enums.UserStatusEnums;
import com.xxyy.entity.vo.FileInfoVO;
import com.xxyy.entity.vo.PagingQueryVO;
import com.xxyy.entity.vo.UserInfoVO;
import com.xxyy.entity.vo.UserSpaceVO;
import com.xxyy.service.IAdminService;
import com.xxyy.utils.CodeConstants;
import com.xxyy.utils.RedisConstants;
import com.xxyy.utils.StringTools;
import com.xxyy.utils.common.AppException;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import javax.servlet.http.HttpServletResponse;
import java.util.*;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;

/**
 * @author xy
 * @date 2024-10-06 14:21
 */

@Service
public class AdminServiceImpl implements IAdminService {

    @Autowired
    UserInfoServiceImpl userInfoService;

    @Autowired
    FileInfoServiceImpl fileInfoService;

    @Autowired
    RecycleBinServiceImpl recycleBinService;


    @Autowired
    StringRedisTemplate stringRedisTemplate;

    @Override
    public PagingQueryVO<UserInfoVO> pageUserList(Page<UserInfo> userInfoPageVO, String nickNameFuzzy, String status) {
        LambdaQueryWrapper<UserInfo> queryWrapper = new LambdaQueryWrapper<>();
        queryWrapper.like(!StringTools.isEmpty(nickNameFuzzy), UserInfo::getNickName, nickNameFuzzy);
        queryWrapper.eq(!StringTools.isEmpty(status), UserInfo::getStatus, status);
        Page<UserInfo> userInfoPage = userInfoService.getBaseMapper().selectPage(userInfoPageVO, queryWrapper);
        return PagingQueryVO.ofUser(userInfoPage);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void updateUserStatus(String userId, String status) {
        UserInfo userInfo = userInfoService.getBaseMapper().selectOne(new LambdaQueryWrapper<UserInfo>().eq(UserInfo::getUserId, userId));
        UserStatusEnums userStatusEnums = UserStatusEnums.getUserStatusEnums(Integer.parseInt(status));
        if (userInfo == null || userStatusEnums == null) {
            throw new AppException(ResponseCodeEnums.CODE_600);
        }
        userInfoService.getBaseMapper().updateById(userInfo.setStatus(Integer.parseInt(status)));
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void updateUserSpace(String userId, String changeSpace) {
        UserInfo userInfo = userInfoService.getBaseMapper().selectOne(new LambdaQueryWrapper<UserInfo>().eq(UserInfo::getUserId, userId));
        if (userInfo == null || StringTools.isEmpty(changeSpace)) {
            throw new AppException(ResponseCodeEnums.CODE_600);
        }
        long size = Long.parseLong(changeSpace) * CodeConstants.MB;
        userInfoService.getBaseMapper().updateUserSpace(userId, null, size);
        String json = stringRedisTemplate.opsForValue().get(RedisConstants.MYPAN_LOGIN_USER_SPACE + userId);
        UserSpaceVO userSpaceVO = null;
        if (!StringTools.isEmpty(json)) {
             userSpaceVO = JSON.parseObject(json, UserSpaceVO.class);
            userSpaceVO.setTotalSpace(size);
        } else {
            Long useSpace = fileInfoService.getBaseMapper().selectUseSpace(userId);
            userSpaceVO = new UserSpaceVO(useSpace, size);
        }
        if (size < userSpaceVO.getUseSpace()) {
            throw new AppException(ResponseCodeEnums.CODE_904);
        }
        stringRedisTemplate.opsForValue().set(RedisConstants.MYPAN_LOGIN_USER_SPACE + userId, JSON.toJSONString(userSpaceVO), 1, TimeUnit.HOURS);
    }

    @Override
    public PagingQueryVO<FileInfoVO> pageFileList(Page<FileInfoVO> fileInfoPage, FileQueryDTO fileQueryDTO) {
        // mybatis-plus 联合查询 查询user表中的nickName字段
        Page<FileInfoVO> fileInfoPageVO = fileInfoService.getBaseMapper().selectJoinPage(fileInfoPage, FileInfoVO.class, new MPJLambdaWrapper<FileInfo>()
                .selectAll(FileInfo.class)
                .select(UserInfo::getNickName)
                .leftJoin(UserInfo.class, UserInfo::getUserId, FileInfo::getUserId)
                .like(!StringTools.isEmpty(fileQueryDTO.getFileNameFuzzy()), FileInfo::getFileName, fileQueryDTO.getFileNameFuzzy())
                .eq(FileInfo::getFilePid, fileQueryDTO.getFilePid()));
        return PagingQueryVO.ofFile(fileInfoPageVO);
    }

    @Override
    public String createDownloadUrl(String userId, String fileId) {
        return fileInfoService.createDownloadUrl(fileId, userId);
    }

    @Override
    public void downloadFile(String code, HttpServletResponse response) {
        fileInfoService.downloadFile(code, response);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void deleteFile(String fileIdAndUserIds) {
        List<String> fileIdAndUserIdList = Arrays.stream(fileIdAndUserIds.split(",")).collect(Collectors.toList());
        Map<String, String> idMap = new HashMap<>();
        for (String fileIdAndUserId : fileIdAndUserIdList) {
            String[] split = fileIdAndUserId.split("_");
            String userId = split[0];
            String fileId = split[1];
            if (idMap.get(userId) == null) {
                idMap.put(userId, fileId);
            } else {
                String fileIds = idMap.get(userId);
                fileIds += "," + fileId;
                idMap.put(userId, fileIds);
            }
        }
        Set<String> userIds = idMap.keySet();
        for (String userId : userIds) {
            // 按照逻辑流程， 先将文件移入回收站， 在进行删除
            fileInfoService.removeFile2RecycleBatch(idMap.get(userId), userId);
            recycleBinService.deleteFile(userId, idMap.get(userId));
        }
    }

    @Override
    public void getVideoInfo(String userId, String fileId, HttpServletResponse response) {
        fileInfoService.getFile(response, fileId, userId);
    }


}
