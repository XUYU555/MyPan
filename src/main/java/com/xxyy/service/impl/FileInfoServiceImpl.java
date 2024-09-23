package com.xxyy.service.impl;

import com.alibaba.fastjson2.JSON;
import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.xxyy.dto.*;
import com.xxyy.entity.FileInfo;
import com.xxyy.entity.enums.*;
import com.xxyy.mapper.FileInfoMapper;
import com.xxyy.service.IFileInfoService;
import com.xxyy.utils.CodeConstants;
import com.xxyy.utils.RedisConstants;
import com.xxyy.utils.StringTools;
import com.xxyy.utils.common.AppException;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Date;
import java.util.List;
import java.util.concurrent.TimeUnit;

/**
 * @author xy
 * @date 2024-09-22 11:05
 */

@Service
public class FileInfoServiceImpl extends ServiceImpl<FileInfoMapper, FileInfo> implements IFileInfoService {

    @Value("${project.folder}")
    private String projectFile;

    @Autowired
    StringRedisTemplate stringRedisTemplate;

    @Override
    public PagingQueryVO pageQueryFile(FileQueryDTO fileQueryDTO, String token) {
        long pageNo = "".equals(fileQueryDTO.getPageNo())? 1 : Long.parseLong(fileQueryDTO.getPageNo());
        long pageSize = "".equals(fileQueryDTO.getPageSize())? 15:Long.parseLong(fileQueryDTO.getPageSize());
        // 设置分页参数
        Page<FileInfo> page = Page.of(pageNo, pageSize);
        FileCategoryEnums categoryEnum = FileCategoryEnums.getCode(fileQueryDTO.getCategory());
        if (categoryEnum == null) {
            throw new AppException(ResponseCodeEnums.CODE_600);
        }
        Object userId = stringRedisTemplate.opsForHash().entries(RedisConstants.MYPAN_LOGIN_USER_KEY + token).get("userId");
        // 添加查询条件
        QueryWrapper<FileInfo> fileQueryWrapper = new QueryWrapper<>();
        fileQueryWrapper.eq("user_id", userId);
        fileQueryWrapper.eq("file_category", categoryEnum.getCode());
        if (!"0".equals(fileQueryDTO.getFilePid())) {
            fileQueryWrapper.eq("file_pid", fileQueryDTO.getFilePid());
        }
        if (!"".equals(fileQueryDTO.getFileNameFuzzy())) {
            fileQueryWrapper.eq("file_name", fileQueryDTO.getFileNameFuzzy());
        }
        // 使用page方法进行分页查询
        Page<FileInfo> pageResult = page(page, fileQueryWrapper);
        return PagingQueryVO.of(pageResult);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public UploadFileVO uploadFile(UploadFileDTO upLoadFileDTO, String token) {
        if (StringTools.isEmpty(upLoadFileDTO.getFileId())) {
            upLoadFileDTO.setFileId(StringTools.getRandomString(CodeConstants.LENGTH_10));
        }
        UploadFileVO uploadFileVO = new UploadFileVO();
        uploadFileVO.setFileId(upLoadFileDTO.getFileId());
        // 获取当前时间
        Date curDate = new Date();
        // 获得用户使用空间
        String userId = (String) stringRedisTemplate.opsForHash().entries(RedisConstants.MYPAN_LOGIN_USER_KEY + token).get("userId");
        UserSpaceVO userSpaceVO = JSON.parseObject(stringRedisTemplate.opsForValue().get(RedisConstants.MYPAN_LOGIN_USER_SPACE + userId), UserSpaceVO.class);
        if (0 == upLoadFileDTO.getChunkIndex()) {
            // 如果分片索引等于0， 则是第一次传输，根据MD5值查询数据库中是否有文件
            QueryWrapper<FileInfo> fileQueryWrapper = new QueryWrapper<>();
            fileQueryWrapper.eq("file_md5", upLoadFileDTO.getFileMd5());
            fileQueryWrapper.eq("status", FileStatusEnums.USING.getCode());
            Page<FileInfo> simplePage = new Page<>(0, 1);
            List<FileInfo> records = page(simplePage, fileQueryWrapper).getRecords();
            // 如果有数据则实现  秒传
            if (!records.isEmpty()) {
                FileInfo dbFileInfo = records.get(0);
                if (dbFileInfo.getFileSize() + userSpaceVO.getUseSpace() > userSpaceVO.getTotalSpace()) {
                    throw new AppException(ResponseCodeEnums.CODE_904);
                }
                // 复制数据库数据
                dbFileInfo.setFileId(upLoadFileDTO.getFileId());
                dbFileInfo.setFilePid(upLoadFileDTO.getFilePid());
                dbFileInfo.setFileMd5(upLoadFileDTO.getFileMd5());
                dbFileInfo.setUserId(userId);
                dbFileInfo.setCreateTime(curDate);
                dbFileInfo.setLastUpdateTime(curDate);
                dbFileInfo.setStatus(FileStatusEnums.USING.getCode());
                dbFileInfo.setDelFlag(FileDelFlagEnums.NORMAL.getCode());
                dbFileInfo.setFileName(fileRename(dbFileInfo.getFilePid(), dbFileInfo.getUserId(), dbFileInfo.getFileName()));
                uploadFileVO.setStatus(UploadStatusEnums.UPLOAD_SECONDS.getCode());
                // 保存到数据库中
                save(dbFileInfo);
                // 更新用户使用空间
                UserSpaceVO userSpace = updateUserSpace(userId, userSpaceVO.getUseSpace(), null);
                String jsonString = JSON.toJSONString(userSpace);
                stringRedisTemplate.opsForValue().set(RedisConstants.MYPAN_LOGIN_USER_SPACE + userId, jsonString);
                return uploadFileVO;
            }
        }
        try {
            // 分片传输数据
            // 判断临时数据是否超过剩余空间
            Long curSize = getCurSize(userId, uploadFileVO.getFileId());
            if (curSize + upLoadFileDTO.getFile().getSize() + userSpaceVO.getUseSpace() > userSpaceVO.getTotalSpace()) {
                throw new AppException(ResponseCodeEnums.CODE_904);
            }
            // 创建临时数据分片存储目录
            String path = projectFile + CodeConstants.TEMP_FILE + userId + "/" + uploadFileVO.getFileId() + "/";
            File file = new File(path);
            if (!file.exists()) {
                file.mkdirs();
            }
            // 创建临时文件存储路径, 并写入本地
            Path tempPath = Paths.get(path + upLoadFileDTO.getChunkIndex());
            Files.write(tempPath, upLoadFileDTO.getFile().getBytes());
            // 存入临时数据到Redis
            stringRedisTemplate.opsForValue().set(RedisConstants.MYPAN_FILE_TEMP_SIZE + userId + ":" + uploadFileVO.getFileId(),
                    String.valueOf((curSize + upLoadFileDTO.getFile().getSize())), 1, TimeUnit.HOURS);
            // 判断分片数据传输是否完成
            if (upLoadFileDTO.getChunkIndex() < upLoadFileDTO.getChunks() - 1) {
                uploadFileVO.setStatus(UploadStatusEnums.UPLOADING.getCode());
            }
            return uploadFileVO;
        } catch (IOException e) {
            log.error("文件上传失败", e);
            throw new AppException("文件上传失败");
        }
    }

    private String fileRename(String filePid, String userId, String fileName) {
        QueryWrapper<FileInfo> fileQueryWrapper = new QueryWrapper<>();
        fileQueryWrapper.eq("file_pid", filePid);
        fileQueryWrapper.eq("user_id", userId);
        fileQueryWrapper.eq("file_name", fileName);
        fileQueryWrapper.eq("status", FileStatusEnums.USING.getCode());
        int count = count(fileQueryWrapper);
        if (count > 0) {
            String[] split = fileName.split("\\.");
            return split[0] + "(" + count + ")" + "." + split[1];
        }
        return fileName;
    }

    private UserSpaceVO updateUserSpace(String userId, Long useSpace, Long totalSpace) {
        UserSpaceVO userSpaceVO = JSON.parseObject(stringRedisTemplate.opsForValue().get(RedisConstants.MYPAN_LOGIN_USER_SPACE + userId), UserSpaceVO.class);
        // 更新使用空间
        if (totalSpace == null) {
            long newUseSpace = userSpaceVO.getUseSpace() + useSpace;
            if (newUseSpace > userSpaceVO.getTotalSpace()) {
                throw new AppException(ResponseCodeEnums.CODE_904);
            }
            userSpaceVO.setUseSpace(newUseSpace);
        }
        // 更新总空间
        if (useSpace == null) {
            long newTotalSpace = userSpaceVO.getTotalSpace() + totalSpace;
            if (newTotalSpace < userSpaceVO.getUseSpace()) {
                throw new AppException(ResponseCodeEnums.CODE_904);
            }
            userSpaceVO.setTotalSpace(newTotalSpace);
        }
        return userSpaceVO;
    }

    private Long getCurSize(String userId, String fileId) {
        String strSize = stringRedisTemplate.opsForValue().get(RedisConstants.MYPAN_FILE_TEMP_SIZE + userId + ":" + fileId);
        if (strSize != null) {
            return Long.valueOf(strSize);
        }
        return 0L;
    }

}
