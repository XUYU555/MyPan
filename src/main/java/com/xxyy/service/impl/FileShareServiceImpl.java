package com.xxyy.service.impl;

import cn.hutool.core.bean.BeanUtil;
import com.alibaba.fastjson2.JSON;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.github.yulichang.wrapper.MPJLambdaWrapper;
import com.xxyy.entity.FileInfo;
import com.xxyy.entity.FileShare;
import com.xxyy.entity.UserInfo;
import com.xxyy.entity.enums.FileDelFlagEnums;
import com.xxyy.entity.enums.FolderTypeEnums;
import com.xxyy.entity.enums.ResponseCodeEnums;
import com.xxyy.entity.enums.ShareValidTypeEnums;
import com.xxyy.entity.vo.FileShareVO;
import com.xxyy.entity.vo.PagingQueryVO;
import com.xxyy.entity.vo.UserSpaceVO;
import com.xxyy.mapper.FileShareMapper;
import com.xxyy.mapper.UserInfoMapper;
import com.xxyy.service.IFileInfoService;
import com.xxyy.service.IFileShareService;
import com.xxyy.service.IUserInfoService;
import com.xxyy.utils.CodeConstants;
import com.xxyy.utils.RedisConstants;
import com.xxyy.utils.StringTools;
import com.xxyy.utils.common.AppException;
import org.aspectj.apache.bcel.classfile.Code;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import javax.annotation.Resource;
import java.util.*;
import java.util.function.Function;
import java.util.stream.Collectors;


/**
 * @author xy
 * @date 2024-10-05 19:07
 */

@Service
public class FileShareServiceImpl extends ServiceImpl<FileShareMapper, FileShare> implements IFileShareService {

    @Autowired
    StringRedisTemplate stringRedisTemplate;

    @Autowired
    FileInfoServiceImpl fileInfoService;

    @Resource
    UserInfoMapper userInfoMapper;

    @Override
    public PagingQueryVO<FileShareVO> pageShareList(String token, Page<FileShareVO> fileSharePage) {
        String userId = (String)stringRedisTemplate.opsForHash().entries(RedisConstants.MYPAN_LOGIN_USER_KEY + token).get("userId");
        // mybatis-plus联合查询 查询File表中的字段加入到FileShare类中
        Page<FileShareVO> fileSharePageVO = getBaseMapper().selectJoinPage(fileSharePage, FileShareVO.class,
                new MPJLambdaWrapper<FileShare>().selectAll(FileShare.class)
                        .select(FileInfo::getFileName).select(FileInfo::getFolderType)
                        .select(FileInfo::getFileCategory).select(FileInfo::getFileType)
                        .select(FileInfo::getFileCover).leftJoin(FileInfo.class, FileInfo::getFileId, FileShare::getFileId)
                        .eq(FileInfo::getDelFlag, FileDelFlagEnums.NORMAL.getCode())
                        .eq(FileInfo::getUserId, userId));
        return PagingQueryVO.ofShare(fileSharePageVO);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public FileShareVO shareFile(String token, String fileId, Integer validType, String code) {
        String userId = (String)stringRedisTemplate.opsForHash().entries(RedisConstants.MYPAN_LOGIN_USER_KEY + token).get("userId");
        ShareValidTypeEnums shareValidType = ShareValidTypeEnums.getShareValidType(validType);
        if (shareValidType == null) {
            throw new AppException(ResponseCodeEnums.CODE_600);
        }
        Date curDate = new Date();
        FileShare fileShare = new FileShare();
        fileShare.setShareTime(curDate);
        fileShare.setType(shareValidType.getType());
        fileShare.setUserId(userId);
        fileShare.setFileId(fileId);
        Calendar calendar = Calendar.getInstance();
        calendar.add(Calendar.DAY_OF_YEAR, shareValidType.getExpire());
        fileShare.setExpireTime(calendar.getTime());
        fileShare.setShareId(StringTools.getRandomString(CodeConstants.LENGTH_20));
        fileShare.setCode(StringTools.isEmpty(code)? StringTools.getRandomString(CodeConstants.LENGTH_5) : code);
        save(fileShare);
        return FileShareVO.of(fileShare, null);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void cancelShare(String token, String shareIds) {
        String userId = (String)stringRedisTemplate.opsForHash().entries(RedisConstants.MYPAN_LOGIN_USER_KEY + token).get("userId");
        if (StringTools.isEmpty(shareIds)) {
            throw new AppException(ResponseCodeEnums.CODE_600);
        }
        List<String> ids = Arrays.stream(shareIds.split(",")).collect(Collectors.toList());
        removeByIds(ids);
    }

    /**
     * 保存分享文件到我的网盘
     * @param shareFileIds  保存文件ids
     * @param myFolderId    保存到的目录
     * @param loginUserId   当前登录用户id
     * @param shareUserId   分享文件用户id
     */
    @Transactional(rollbackFor = Exception.class)
    public void saveShareFile2MyPan(List<String> shareFileIds, String myFolderId, String loginUserId, String shareUserId) {
        // 获取保存目录下的文件名 (用于检测文件名是否重复)
        List<FileInfo> list = fileInfoService.list(new LambdaQueryWrapper<FileInfo>().eq(FileInfo::getUserId, loginUserId)
                .eq(FileInfo::getFilePid, myFolderId).
                eq(FileInfo::getDelFlag, FileDelFlagEnums.NORMAL.getCode()));
        Map<String, FileInfo> folderFiles = list.stream().collect(Collectors.toMap(FileInfo::getFileName, Function.identity(), (item1, item2) -> item2));
        // 查询分享文件信息
        List<FileInfo> shareFiles = fileInfoService.list(new LambdaQueryWrapper<FileInfo>().eq(FileInfo::getUserId, shareUserId)
                .in(FileInfo::getFileId, shareFileIds)
                .eq(FileInfo::getDelFlag, FileDelFlagEnums.NORMAL.getCode()));
        // 需要保存到我的网盘的文件集合
        Date curDate = new Date();
        List<FileInfo> resultFileInfos = new ArrayList<>();
        for (FileInfo shareFile : shareFiles) {
            findFolderAndSubFile(resultFileInfos, shareFile, shareUserId, folderFiles, curDate, myFolderId, loginUserId);
        }
        long totalSize = 0;
        for (FileInfo shareFile : resultFileInfos) {
            if (shareFile.getFolderType().equals(FolderTypeEnums.DOCUMENT.getType())) {
                totalSize += shareFile.getFileSize();
            }
        }
        UserInfo userInfo = userInfoMapper.selectById(loginUserId);
        if (userInfo.getUseSpace() + totalSize > userInfo.getTotalSpace()) {
            throw new AppException(ResponseCodeEnums.CODE_904);
        }
        fileInfoService.saveBatch(resultFileInfos);
        // 更新Redis中的空间
        fileInfoService.updateUserSpace(loginUserId, totalSize, null);
        // 更新数据库数据
        userInfoMapper.updateUserSpace(loginUserId, totalSize, null);
    }

    private void findFolderAndSubFile(List<FileInfo> resultFileInfos, FileInfo shareFile, String shareUserId, Map<String, FileInfo> folderFiles,
                                      Date curDate, String filePid, String loginUserId) {
        FileInfo fileInfo = new FileInfo();
        BeanUtil.copyProperties(shareFile, fileInfo);
        fileInfo.setLastUpdateTime(curDate);
        fileInfo.setCreateTime(curDate);
        if (folderFiles.get(fileInfo.getFileName()) != null) {
            fileInfo.setFileName(rename(shareFile.getFileName()));
        }
        fileInfo.setUserId(loginUserId);
        fileInfo.setFileId(StringTools.getRandomString(CodeConstants.LENGTH_10));
        if (shareFile.getFolderType().equals(FolderTypeEnums.FOLDER.getType())) {
            List<FileInfo> list = fileInfoService.list(new LambdaQueryWrapper<FileInfo>()
                    .eq(FileInfo::getFilePid, shareFile.getFileId()).eq(FileInfo::getUserId, shareUserId));
            for (FileInfo info : list) {
                findFolderAndSubFile(resultFileInfos, info, shareUserId, folderFiles, curDate, fileInfo.getFileId(), loginUserId);
            }
        }
        fileInfo.setFilePid(filePid);
        resultFileInfos.add(fileInfo);
    }

    private String rename(String fileName) {
        String fileSuffix = StringTools.getFileSuffix(fileName);
        String fileNameNoSuffix = StringTools.getFileNameNoSuffix(fileName);
        String newFileName = fileNameNoSuffix + "_" + StringTools.getRandomString(CodeConstants.LENGTH_5);
        return newFileName + "." + fileSuffix;
    }
}
