package com.xxyy.service.impl;

import com.alibaba.fastjson2.JSON;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.xxyy.entity.FileInfo;
import com.xxyy.entity.dto.*;
import com.xxyy.entity.enums.*;
import com.xxyy.entity.vo.*;
import com.xxyy.mapper.FileInfoMapper;
import com.xxyy.mapper.UserInfoMapper;
import com.xxyy.service.IFileInfoService;
import com.xxyy.utils.*;
import com.xxyy.utils.common.AppException;
import io.minio.GetObjectResponse;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.io.FileUtils;
import org.apache.commons.lang3.StringUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.ApplicationContext;
import org.springframework.context.annotation.Lazy;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;


import javax.annotation.Resource;
import javax.servlet.ServletOutputStream;
import javax.servlet.http.HttpServletResponse;
import java.io.*;
import java.net.URLEncoder;
import java.text.SimpleDateFormat;
import java.util.*;
import java.util.concurrent.*;
import java.util.stream.Collectors;

/**
 * @author xy
 * @date 2024-09-22 11:05
 */

@Service
@Slf4j
public class FileInfoServiceImpl extends ServiceImpl<FileInfoMapper, FileInfo> implements IFileInfoService {

    @Value("${project.folder}")
    private String projectFile;

    @Resource
    private MinioClientUtils minioClientUtils;

    @Autowired
    private StringRedisTemplate stringRedisTemplate;

    @Resource
    private UserInfoMapper userInfoMapper;

    // 获取代理对象
    @Autowired
    private ApplicationContext applicationContext;


    private static final ExecutorService executorService;
    static {
        executorService = new ThreadPoolExecutor(6, 10, 60L, TimeUnit.SECONDS,
                new LinkedBlockingDeque<>(100), new ThreadPoolExecutor.CallerRunsPolicy());
    }

    @Override
    public PagingQueryVO<FileInfoVO> pageQueryFile(FileQueryDTO fileQueryDTO, String token) {
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
        LambdaQueryWrapper<FileInfo> fileQueryWrapper = new LambdaQueryWrapper<>();
        fileQueryWrapper.eq(FileInfo::getUserId, userId);
        fileQueryWrapper.eq(FileInfo::getFilePid, fileQueryDTO.getFilePid() == null? 0: fileQueryDTO.getFilePid());
        fileQueryWrapper.eq(FileInfo::getDelFlag, FileDelFlagEnums.NORMAL.getCode());
        if (categoryEnum.getCode() != 0) {
            fileQueryWrapper.eq(FileInfo::getFileCategory, categoryEnum.getCode());
        }
        if (!"".equals(fileQueryDTO.getFileNameFuzzy())) {
            fileQueryWrapper.like(FileInfo::getFileName, fileQueryDTO.getFileNameFuzzy());
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
        Date curDate = new Date();
        // 获得用户使用空间
        String userId = (String) stringRedisTemplate.opsForHash().entries(RedisConstants.MYPAN_LOGIN_USER_KEY + token).get("userId");
        UserSpaceVO userSpaceVO = JSON.parseObject(stringRedisTemplate.opsForValue().get(RedisConstants.MYPAN_LOGIN_USER_SPACE + userId), UserSpaceVO.class);
        if (0 == upLoadFileDTO.getChunkIndex()) {
            // 如果分片索引等于0， 则是第一次传输，根据MD5值查询数据库中是否有文件
            LambdaQueryWrapper<FileInfo> fileQueryWrapper = new LambdaQueryWrapper<>();
            fileQueryWrapper.eq(FileInfo::getFileMd5, upLoadFileDTO.getFileMd5());
            fileQueryWrapper.and(queryWrapper -> queryWrapper.eq(FileInfo::getStatus, FileStatusEnums.USING.getCode())
                    .or().eq(FileInfo::getStatus, FileStatusEnums.TRANSFER.getCode()));
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
                dbFileInfo.setFileName(rename(dbFileInfo.getFilePid(), dbFileInfo.getUserId(), dbFileInfo.getFileName()));
                uploadFileVO.setStatus(UploadStatusEnums.UPLOAD_SECONDS.getCode());
                // 保存到数据库中
                save(dbFileInfo);
                // 更新用户使用空间
                updateUserSpace(userId, userSpaceVO.getUseSpace(), null);
                userInfoMapper.updateUserSpace(userId, userSpaceVO.getUseSpace(), null);
                return uploadFileVO;
            }
        }
        // 分片传输数据
        // 判断临时数据是否超过剩余空间
        long curSize = getCurSize(userId, uploadFileVO.getFileId());
        if (curSize + upLoadFileDTO.getFileSize() + userSpaceVO.getUseSpace() > userSpaceVO.getTotalSpace()) {
            // TODO: 2024/9/25 断点续传 删除临时文件大小问题
            // 删除临时文件大侠redis数据
            stringRedisTemplate.delete(RedisConstants.MYPAN_FILE_TEMP_SIZE + userId + ":" + uploadFileVO.getFileId());
            throw new AppException(ResponseCodeEnums.CODE_904);
        }
        // TODO: 2025/3/16 创建预签命URL
        String partPresignedURL = null;
        try {
            partPresignedURL = minioClientUtils.getPartPresignedURL(upLoadFileDTO.getFileId(), upLoadFileDTO.getChunkIndex());
        } catch (Exception e) {
            throw new AppException("创建预签命链接失败", e);
        }
        uploadFileVO.setPresignedUrl(partPresignedURL);
        // 存入临时文件的大小到Redis
        stringRedisTemplate.opsForValue().set(RedisConstants.MYPAN_FILE_TEMP_SIZE + userId + ":" + uploadFileVO.getFileId(),
                String.valueOf((curSize + upLoadFileDTO.getFileSize())), 2, TimeUnit.HOURS);
        // 判断分片数据传输是否完成
        if (upLoadFileDTO.getChunkIndex() < upLoadFileDTO.getChunks() - 1) {
            uploadFileVO.setStatus(UploadStatusEnums.UPLOADING.getCode());
        } else {
            uploadFileVO.setStatus(UploadStatusEnums.UPLOAD_FINISH.getCode());
        }
        return uploadFileVO;
    }

    @Override
    public String  getImage(String folder, String fileName) {
        String minioPath = folder + "/" + fileName;
        if (minioPath.contains("../") || minioPath.contains("..\\")) {
            return "";
        }
        String redisKey = RedisConstants.MYPAN_PRESIGNEDURL_IMG + minioPath;
        String url = stringRedisTemplate.opsForValue().get(redisKey);
        if (!StringTools.isEmpty(url)) {
            return url;
        }
        try {
            String presignedUrl = minioClientUtils.getPresignedUrl(minioPath);
            stringRedisTemplate.opsForValue().set(redisKey, presignedUrl, 6, TimeUnit.HOURS);
            return presignedUrl;
        } catch (Exception e) {
            log.error("获取图片文件：{}失败 ", minioPath, e);
            throw new AppException(ResponseCodeEnums.CODE_500);
        }
    }


    /**
     * fileId 有两种情况
     * 如果为 XXXXX 则是文件id
     * XXXXXXX-000X.ts 则是ts文件
     * @param response
     * @param fileId
     * @param token
     */
    // TODO: 2024/9/28 可能存在bug
    @Override
    public void getFile(HttpServletResponse response, String fileId, String token) {
        String targetPath = null;
        if (fileId.contains("../") || fileId.contains("..\\")) {
            response.setStatus(HttpServletResponse.SC_NOT_FOUND);
            return;
        }
        String userId = (String) stringRedisTemplate.opsForHash().entries(RedisConstants.MYPAN_LOGIN_USER_KEY + token).get("userId");
        // 兼容管理端和外部分享端 获取文件内容接口
        if (userId == null && token.length() == CodeConstants.LENGTH_10) {
            userId = token;
        }
        FileInfo fileInfo = getOne(new LambdaQueryWrapper<FileInfo>().eq(FileInfo::getFileId, fileId).eq(FileInfo::getUserId, userId));
        if (fileInfo == null) {
            // 请求的是ts文件
            String realFileId = fileId.split("-")[0];
            fileInfo = getOne(new LambdaQueryWrapper<FileInfo>().eq(FileInfo::getFileId, realFileId));
            targetPath = fileInfo.getFilePath().split("\\.")[0] + "/" + fileId;
        } else if (fileInfo.getFileType().intValue() == FileTypeEnums.VIDEO.getType().intValue()) {
            // 获取m3u8索引文件
            targetPath = fileInfo.getFilePath().split("\\.")[0] + "/" + CodeConstants.M3U8_NAME;
        } else {
            // 其他文件
            targetPath = fileInfo.getFilePath();
        }
        try(InputStream inputStream = minioClientUtils.downloadVideoFile(targetPath);
            ServletOutputStream outputStream = response.getOutputStream()) {
            int len = 0;
            byte[] bytes = new byte[1024];
            while((len = inputStream.read(bytes)) != -1) {
                outputStream.write(bytes, 0, len);
            }
            // 确保刷新并关闭
            outputStream.flush();
        } catch (IOException e) {
            throw new AppException("写入或读取文件失败");
        } catch (Exception e) {
            throw new AppException(ResponseCodeEnums.CODE_500);
        }
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public FileInfoVO createFolder(String filePid, String folderName, String token) {
        String userId = (String) (stringRedisTemplate.opsForHash().entries(RedisConstants.MYPAN_LOGIN_USER_KEY + token).get("userId"));
        checkFileName(filePid, folderName, userId, FolderTypeEnums.FOLDER);
        Date curDate = new Date();
        FileInfo fileInfo = new FileInfo();
        fileInfo.setFilePid(filePid);
        fileInfo.setFileName(folderName);
        fileInfo.setUserId(userId);
        fileInfo.setFolderType(FolderTypeEnums.FOLDER.getType());
        fileInfo.setStatus(FileStatusEnums.USING.getCode());
        fileInfo.setCreateTime(curDate);
        fileInfo.setLastUpdateTime(curDate);
        fileInfo.setDelFlag(FileDelFlagEnums.NORMAL.getCode());
        fileInfo.setFileId(StringTools.getRandomString(CodeConstants.LENGTH_10));
        save(fileInfo);
        return FileInfoVO.of(fileInfo);
    }

    @Override
    public List<FolderInfoVO> getFolderInfo(String path, String token) {
        String userId = (String) stringRedisTemplate.opsForHash().entries(RedisConstants.MYPAN_LOGIN_USER_KEY + token).get("userId");
        if (StringTools.isEmpty(path)) {
            return null;
        }
        // 兼容外部分享端 获取路径信心接口
        if (userId == null && token.length() == CodeConstants.LENGTH_10) {
            userId = token;
        }
        String[] paths = path.split("/");
        String join = StringUtils.join(paths, "\",\"");
        List<String> ids = Arrays.stream(paths).collect(Collectors.toList());
        List<FileInfo> fileList = list(new LambdaQueryWrapper<FileInfo>().in(FileInfo::getFileId, ids)
                .eq(FileInfo::getUserId, userId)
                .eq(FileInfo::getFolderType, FolderTypeEnums.FOLDER.getType())
                // 根据前端传输的 数据顺序排序 order by field
                .last("ORDER BY FIELD(file_id, " + '\"' + join + '\"' + ")"));
        return fileList.stream().map(FolderInfoVO::of).collect(Collectors.toList());
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public FileInfoVO fileRename(String token,String fileId, String fileName) {
        String userId = (String) stringRedisTemplate.opsForHash().entries(RedisConstants.MYPAN_LOGIN_USER_KEY + token).get("userId");
        FileInfo fileInfo = getOne(new LambdaQueryWrapper<FileInfo>().eq(FileInfo::getUserId, userId).eq(FileInfo::getFileId, fileId));
        if (fileInfo == null) {
            throw new AppException(ResponseCodeEnums.CODE_600);
        }
        checkFileName(fileInfo.getFilePid(), fileName, userId, FolderTypeEnums.DOCUMENT);
        fileInfo.setFileName(fileName + "." + StringTools.getFileSuffix(fileInfo.getFileName()));
        updateById(fileInfo);
        return FileInfoVO.of(fileInfo);
    }

    @Override
    public List<FileInfoVO> getAllFolder(String token, String filePid, String currentFileIds) {
        String userId = (String) stringRedisTemplate.opsForHash().entries(RedisConstants.MYPAN_LOGIN_USER_KEY + token).get("userId");
        List<FileInfo> list = list(new LambdaQueryWrapper<FileInfo>().eq(FileInfo::getUserId, userId)
                .eq(FileInfo::getFolderType, FolderTypeEnums.FOLDER.getType()).eq(FileInfo::getFilePid, filePid)
                .eq(FileInfo::getStatus, FileStatusEnums.USING.getCode())
                .eq(FileInfo::getDelFlag, FileDelFlagEnums.NORMAL.getCode())
                .orderByDesc(FileInfo::getLastUpdateTime)
                .notIn(FileInfo::getFileId, currentFileIds));
        if (list == null || list.isEmpty()) {
            return new ArrayList<>();
        }
        return list.stream().map(FileInfoVO::of).collect(Collectors.toList());
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void changeFileFolder(String token, String fileIds, String filePid) {
        String userId = (String) stringRedisTemplate.opsForHash().entries(RedisConstants.MYPAN_LOGIN_USER_KEY + token).get("userId");
        // 查询目标目录下的文件
        List<FileInfo> list = list(new LambdaQueryWrapper<FileInfo>().eq(FileInfo::getFilePid, filePid).eq(FileInfo::getUserId, userId)
                .eq(FileInfo::getStatus, FileStatusEnums.USING.getCode()).eq(FileInfo::getDelFlag, FileDelFlagEnums.NORMAL.getCode()));
        Map<String, String> fileMap = list.stream().collect(Collectors.toMap(FileInfo::getFileName, FileInfo::getFileId));
        // 查询需要移动的文件
        Object[] array = Arrays.stream(fileIds.split(",")).toArray();
        List<FileInfo> files = list(new LambdaQueryWrapper<FileInfo>().eq(FileInfo::getUserId, userId).in(FileInfo::getFileId, array)
                .eq(FileInfo::getStatus, FileStatusEnums.USING.getCode()).eq(FileInfo::getDelFlag, FileDelFlagEnums.NORMAL.getCode()));
        for (FileInfo file : files) {
            if (fileMap.get(file.getFileName()) != null){
                // 存在重名文件
                String fileName = file.getFileName();
                String newName = StringTools.getFileNameNoSuffix(fileName) + StringTools.getRandomString(CodeConstants.LENGTH_5);
                file.setFileName(newName + "." + StringTools.getFileSuffix(fileName));
            }
            file.setFilePid(filePid);
            updateById(file);
        }
    }

    @Override
    public String createDownloadUrl(String fileId, String token) {
        String userId = (String) stringRedisTemplate.opsForHash().entries(RedisConstants.MYPAN_LOGIN_USER_KEY + token).get("userId");
        // 兼容管理端和外部分享端的 下载功能
        if (userId == null && token.length() == CodeConstants.LENGTH_10) {
            userId = token;
        }
        String url = stringRedisTemplate.opsForValue().get(RedisConstants.MYPAN_PRESIGNEDURL_FILE + fileId);
        if (!StringTools.isEmpty(url)) {
            return url;
        }
        FileInfo fileInfo = getOne(new LambdaQueryWrapper<FileInfo>().eq(FileInfo::getFileId, fileId).eq(FileInfo::getUserId, userId));
        if (fileInfo == null || fileInfo.getFolderType().intValue() == FolderTypeEnums.FOLDER.getType().intValue()) {
            throw new AppException(ResponseCodeEnums.CODE_600);
        }
        String downloadUrl = null;
        try {
            downloadUrl = minioClientUtils.createDownloadUrl(fileInfo.getFilePath(), fileInfo.getFileName());
        } catch (Exception e) {
            throw new AppException(ResponseCodeEnums.CODE_600);
        }
        stringRedisTemplate.opsForValue().set(RedisConstants.MYPAN_PRESIGNEDURL_FILE + fileId, downloadUrl, 5, TimeUnit.MINUTES);
        return downloadUrl;
    }

    @Override
    public void downloadFile(String code, HttpServletResponse response) {
        String fileJson = stringRedisTemplate.opsForValue().get(RedisConstants.MYPAN_DOWNLOAD_CODE + code);
        if (fileJson == null || StringTools.isEmpty(fileJson)) {
            throw new AppException(ResponseCodeEnums.CODE_600);
        }
        FileDownloadDTO fileDownloadDTO = JSON.parseObject(fileJson, FileDownloadDTO.class);
        String filePath = projectFile + CodeConstants.FILE + fileDownloadDTO.getFilePath();
        File file = new File(filePath);
        if (!file.exists()) {
            return;
        }
        try(FileInputStream inputStream = new FileInputStream(file);
            ServletOutputStream outputStream = response.getOutputStream()) {
            String fileName = URLEncoder.encode(fileDownloadDTO.getFileName(), CodeConstants.CODE_UTF_8);
            response.setContentType("application/octet-stream");
            // 固定格式，指示浏览器下载文件的文件名
            response.setHeader("Content-Disposition", "attachment;filename="+fileName);
            int len = 0;
            byte[] buffer = new byte[1024 * 8];
            while ((len = inputStream.read(buffer)) != -1) {
                outputStream.write(buffer, 0, len);
            }
            outputStream.flush();
        } catch (IOException e) {
            log.error("文件下载失败:{}", filePath);
            throw new AppException("文件下载失败");
        }
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void removeFile2RecycleBatch(String fileIds, String token) {
        String userId = (String) stringRedisTemplate.opsForHash().entries(RedisConstants.MYPAN_LOGIN_USER_KEY + token).get("userId");
        // 兼容admin端的 接口
        if (userId == null && token.length() == CodeConstants.LENGTH_10) {
            userId = token;
        }
        Date curDate = new Date();
        if (StringTools.isEmpty(fileIds)) {
            throw new AppException(ResponseCodeEnums.CODE_600);
        }
        List<String> ids = Arrays.stream(fileIds.split(",")).collect(Collectors.toList());
        // 文件
        List<FileInfo> fileList = getBaseMapper().findUsingFileList(userId, ids, FolderTypeEnums.DOCUMENT);
        // 目录(需要将目录下的文件全部删除)
        List<FileInfo> folderList = getBaseMapper().findUsingFileList(userId, ids, FolderTypeEnums.FOLDER);
        List<FileInfo> subFolderFiles = new ArrayList<>();
        for (FileInfo fileInfo : folderList) {
            findFileInFolder(subFolderFiles, fileList, fileInfo, userId);
        }
        List<FileInfo> fileInfos = fileList.stream().peek(fileInfo -> {
            fileInfo.setRecoveryTime(curDate);
            fileInfo.setDelFlag(FileDelFlagEnums.RECOVERY.getCode());
        }).collect(Collectors.toList());
        fileInfos.addAll(subFolderFiles.stream()
                .peek(fileInfo -> {
                    // 目录下的文件直接定义为删除，方便回收站还原
                    fileInfo.setDelFlag(FileDelFlagEnums.DELETE.getCode());
                    fileInfo.setRecoveryTime(curDate);
                }).collect(Collectors.toList()));
        updateBatchById(fileInfos);
    }


    @Override
    public void notifyPartUploaded(UploadFileDTO uploadFileDTO, String token) {
        String userId = (String) stringRedisTemplate.opsForHash().entries(RedisConstants.MYPAN_LOGIN_USER_KEY + token).get("userId");
        if (userId == null) {
            throw new AppException(ResponseCodeEnums.CODE_901);
        }
        executorService.submit(() -> {
            FileInfoServiceImpl proxy = applicationContext.getBean(FileInfoServiceImpl.class);
            proxy._notifyPartUploaded(uploadFileDTO, userId);
        });
    }

    /**
     * 在MinIo中合并文件
     * @param uploadFileDTO 文件信息
     */
    @Transactional(rollbackFor = Exception.class)
    public void _notifyPartUploaded(UploadFileDTO uploadFileDTO, String userId) {
        try {
            Date cur = new Date();
            String fileSuffix = StringTools.getFileSuffix(uploadFileDTO.getFileName());
            FileTypeEnums fileTypeEnums = FileTypeEnums.getFileTypeBySuffix("." + fileSuffix);
            String month = new SimpleDateFormat("YYYYMM").format(cur);
            String dbPath = month + "/" + userId + uploadFileDTO.getFileId() + "." + fileSuffix;
            if (CodeConstants.CN_TUPIAN.equals(fileTypeEnums.getDesc())) {
                dbPath = month + "/" + userId + uploadFileDTO.getFileId() + CodeConstants.IMAGE_FILE_SUFFIX;
            }
            minioClientUtils.mergeShardFile(uploadFileDTO, dbPath);
            Long totalUseSize = getCurSize(userId, uploadFileDTO.getFileId());
            // 将文件数据插入到数据库中
            FileInfo fileInfo = new FileInfo();
            fileInfo.setUserId(userId);
            fileInfo.setFilePid(uploadFileDTO.getFilePid());
            fileInfo.setFileName(uploadFileDTO.getFileName());
            fileInfo.setFileMd5(uploadFileDTO.getFileMd5());
            fileInfo.setFileId(uploadFileDTO.getFileId());
            fileInfo.setFileType(fileTypeEnums.getType());
            fileInfo.setCreateTime(cur);
            fileInfo.setLastUpdateTime(cur);
            fileInfo.setDelFlag(FileDelFlagEnums.NORMAL.getCode());
            // 设置文件为转码中，当文件分片合并完毕时修改
            fileInfo.setStatus(FileStatusEnums.TRANSFER.getCode());
            fileInfo.setFolderType(FolderTypeEnums.DOCUMENT.getType());
            fileInfo.setFileCategory(fileTypeEnums.getCategory().getCode());
            fileInfo.setFileSize(totalUseSize);
            fileInfo.setFilePath(dbPath);
            save(fileInfo);
            // 更新用户使用空间
            updateUserSpace(userId, totalUseSize, null);
            userInfoMapper.updateUserSpace(userId, totalUseSize, null);
            // 设置事务管理，在当前事务提交后在执行
            TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
                @Override
                public void afterCommit() {
                    // 使用线程池处理转码任务
                    executorService.submit(() -> handlerSpecialFile(uploadFileDTO.getFileId(), userId));
                }
            });
        } catch (Exception e) {
            throw new AppException("文件合并失败" + uploadFileDTO.getFileId(), e);
        } finally {
            // 清除临时文件，不管是否合并完成或失败
            minioClientUtils.removeTempFiles(uploadFileDTO.getChunks(), uploadFileDTO.getFileId());
        }
    }


    private void handlerSpecialFile(String fileId, String userId) {
        boolean handlerSuccess = true;
        FileInfo fileInfo = null;
        String cover = null;
        try {
            fileInfo = getOne(new LambdaQueryWrapper<FileInfo>().eq(FileInfo::getFileId, fileId).eq(FileInfo::getUserId, userId));
            if (fileInfo == null || fileInfo.getStatus().intValue() != FileStatusEnums.TRANSFER.getCode().intValue()) {
                return;
            }
            try (GetObjectResponse fileStream = minioClientUtils.getFileStream(fileInfo.getFilePath())) {
                // 处理图片文件
                if (fileInfo.getFileType().intValue() == FileTypeEnums.IMAGE.getType().intValue()) {
                    // 从minIO中获得文件流
                    cover = coverThumbnail(fileInfo.getFilePath(), fileStream);
                } else if (fileInfo.getFileType().intValue() == FileTypeEnums.VIDEO.getType().intValue()) {
                    // TODO: 2025/3/18 切割视频文件
                    cover = videoCutting(fileInfo.getFilePath(), fileId, fileStream);
                }
            }
        } catch (Exception e) {
            handlerSuccess = false;
            log.error("文件转码失败,文件ID：{}", fileId, e);
        } finally {
            fileInfo.setFileCover(cover);
            fileInfo.setStatus(handlerSuccess? FileStatusEnums.USING.getCode(): FileStatusEnums.TRANSFER_FAIL.getCode());
            updateById(fileInfo);
        }
    }

    private String coverThumbnail(String filePath, GetObjectResponse fileStream) {
        String targetPath = filePath.split("\\.")[0] + "_" + CodeConstants.IMAGE_FILE_SUFFIX;
        try {
            List<String> command = Arrays.asList("ffmpeg", "-i", "pipe:0", "-y", "-f", "image2", "-t", "0.001", "-s",
                    CodeConstants.RESOLUTION_150, "pipe:1");
            ByteArrayOutputStream outputStream = ProcessUtils.streamCutCover(command, fileStream);
            byte[] coverByteArray = outputStream.toByteArray();
            try (ByteArrayInputStream inputStream = new ByteArrayInputStream(coverByteArray)) {
                minioClientUtils.putCover(inputStream, targetPath, coverByteArray.length);
            } catch (Exception e) {
                log.error("缩略图上传失败:{}", filePath, e);
            }
            outputStream.close();
        } catch (Exception e) {
            throw new AppException("获取图片缩略图失败", e);
        }
        return targetPath;
    }

    private String videoCutting(String filePath, String fileId, GetObjectResponse fileInputStream) throws Exception {
        String targetPath = filePath.split("\\.")[0] + "_" + CodeConstants.IMAGE_FILE_SUFFIX;
        String sourcePath = projectFile + CodeConstants.FILE + filePath;
        // ts文件存放路径
        String tsPath = projectFile + CodeConstants.FILE + filePath.split("\\.")[0];
        File directory = new File(tsPath);
        File tsFile = directory;
        if (!tsFile.exists()) {
            tsFile.mkdirs();
        }
        // TODO: 2025/3/18 直接从minIO中下载视频之后再进行处理
        try (FileOutputStream outputStream = new FileOutputStream(sourcePath)) {
            byte[] buffer = new byte[8192];
            int len;
            while((len = fileInputStream.read(buffer)) != -1) {
                outputStream.write(buffer, 0, len);
            }
            outputStream.flush();
        }
        String tsPathName = tsPath + "/" + CodeConstants.TS_NAME;
        // 将mp4 转化为 ts视频文件 "ffmpeg -y -i %s -vcodec copy -acodec copy -bsf:v h264_mp4toannexb %s"
        List<String> tsList = Arrays.asList
                ("ffmpeg", "-y", "-i", sourcePath, "-vcodec", "copy", "-acodec", "copy", "-bsf:v", "h264_mp4toannexb", tsPathName);
        ProcessUtils.executeCommand(tsList);
        // 将ts视频文件，切割成30秒一个的切片  ffmpeg -i %s -c copy -map 0 -f segment -segment-list %s -segment_time 30 %s/%s-%%4d.ts
        String cmdCutTs = "%s"+ File.separator + "%s-%%4d.ts";
        String cutName = String.format(cmdCutTs, tsPath, fileId);
        String m3u8Path = tsPath + File.separator + CodeConstants.M3U8_NAME;
        List<String> tsCutList = Arrays.asList(
                "ffmpeg", "-i", tsPathName, "-c", "copy", "-map", "0", "-f", "segment", "-segment_list", m3u8Path, "-segment_time", "20", cutName);
        ProcessUtils.executeCommand(tsCutList);
        // 生成缩略图
        String cover = videoCover(sourcePath, targetPath);
        // 删除index.ts文件
        new File(tsPathName).delete();
        new File(sourcePath).delete();
        for (File cutFile : Objects.requireNonNull(directory.listFiles())) {
            minioClientUtils.uploadM3U8File(cutFile, filePath.split("\\.")[0] + "/" + cutFile.getName());
            cutFile.delete();
        }
        return cover;
    }

    private String videoCover(String sourcePath, String targetPath) {
        String targetFileName = projectFile + CodeConstants.FILE + targetPath;
        List<String> command = Arrays.asList("ffmpeg", "-i", sourcePath, "-y", "-f", "image2", "-t", "0.001", "-s",
                CodeConstants.RESOLUTION_150, targetFileName);
        ProcessUtils.executeCommand(command);
        // 上传到minIO
        File file = new File(targetFileName);
        try(FileInputStream inputStream = new FileInputStream(targetFileName)) {
            minioClientUtils.putCover(inputStream, targetPath, file.length());
            file.delete();
        } catch (Exception e) {
            throw new AppException("上传视频封面图失败", e);
        }
        return targetPath;
    }

    private void findFileInFolder(List<FileInfo> subFolderFiles, List<FileInfo> fileList, FileInfo fileInfo, String userId) {
        if(fileInfo.getFolderType().intValue() == FolderTypeEnums.FOLDER.getType().intValue()) {
            List<FileInfo> list = list(new LambdaQueryWrapper<FileInfo>().eq(FileInfo::getUserId, userId).eq(FileInfo::getFilePid, fileInfo.getFileId())
                    .eq(FileInfo::getStatus, FileStatusEnums.USING.getCode()).eq(FileInfo::getDelFlag, FileDelFlagEnums.NORMAL.getCode()));
            for (FileInfo info : list) {
                findFileInFolder(subFolderFiles, fileList, info, userId);
            }
            // 将目录放入回收站中
            fileList.add(fileInfo);
        }else {
            // 将目录下的文件同一放入回收站
            subFolderFiles.add(fileInfo);
        }
    }

    private void checkFileName(String filePid, String fileName, String userId, FolderTypeEnums folderTypeEnums) {
        FileInfo fileInfo = getOne(new LambdaQueryWrapper<FileInfo>()
                .eq(FileInfo::getFilePid, filePid)
                .eq(FileInfo::getFileName, fileName)
                .eq(FileInfo::getUserId, userId)
                .eq(FileInfo::getFolderType, folderTypeEnums.getType())
                .eq(FileInfo::getDelFlag, FileDelFlagEnums.NORMAL.getCode())
                .eq(FileInfo::getStatus, FileStatusEnums.USING.getCode()));
        if (fileInfo != null) {
            throw new AppException("文件名已存在");
        }
    }

    public String rename(String filePid, String userId, String fileName) {
        LambdaQueryWrapper<FileInfo> fileQueryWrapper = new LambdaQueryWrapper<>();
        fileQueryWrapper.eq(FileInfo::getFilePid, filePid);
        fileQueryWrapper.eq(FileInfo::getUserId, userId);
        fileQueryWrapper.eq(FileInfo::getFileName, fileName);
        fileQueryWrapper.eq(FileInfo::getStatus, FileStatusEnums.USING.getCode());
        long count = count(fileQueryWrapper);
        if (count > 0) {
            String[] split = fileName.split("\\.");
            int endIndex = split[split.length - 1].length() + 1;
            return fileName.substring(0, fileName.length() - endIndex) + "(" + count + ")." + split[split.length - 1];
        }
        return fileName;
    }


    public void updateUserSpace(String userId, Long useSpace, Long totalSpace) {
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
        String jsonString = JSON.toJSONString(userSpaceVO);
        stringRedisTemplate.opsForValue().set(RedisConstants.MYPAN_LOGIN_USER_SPACE + userId, jsonString, 1, TimeUnit.HOURS);
    }

    private Long getCurSize(String userId, String fileId) {
        String strSize = stringRedisTemplate.opsForValue().get(RedisConstants.MYPAN_FILE_TEMP_SIZE + userId + ":" + fileId);
        if (strSize != null) {
            return Long.valueOf(strSize);
        }
        return 0L;
    }


    public boolean checkFilePid(String filePid, String fileId, String userId) {
        if (filePid.equals(fileId)) {
            return true;
        }
        FileInfo fileInfo = getOne(new LambdaQueryWrapper<FileInfo>().eq(FileInfo::getFileId, filePid)
                .eq(FileInfo::getUserId, userId).eq(FileInfo::getDelFlag, FileDelFlagEnums.NORMAL.getCode()));
        if (fileInfo == null) {
            return false;
        }
        return checkFilePid(fileInfo.getFilePid(), fileId, userId);
    }
}
