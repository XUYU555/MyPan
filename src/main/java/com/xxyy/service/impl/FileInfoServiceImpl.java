package com.xxyy.service.impl;

import com.alibaba.fastjson2.JSON;
import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.xxyy.dto.*;
import com.xxyy.entity.FileInfo;
import com.xxyy.entity.enums.*;
import com.xxyy.mapper.FileInfoMapper;
import com.xxyy.mapper.UserInfoMapper;
import com.xxyy.service.IFileInfoService;
import com.xxyy.utils.CodeConstants;
import com.xxyy.utils.ProcessUtils;
import com.xxyy.utils.RedisConstants;
import com.xxyy.utils.StringTools;
import com.xxyy.utils.common.AppException;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.io.FileUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
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
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.text.SimpleDateFormat;
import java.util.*;
import java.util.concurrent.TimeUnit;

/**
 * @author xy
 * @date 2024-09-22 11:05
 */

@Service
@Slf4j
public class FileInfoServiceImpl extends ServiceImpl<FileInfoMapper, FileInfo> implements IFileInfoService {

    @Value("${project.folder}")
    private String projectFile;

    @Autowired
    private StringRedisTemplate stringRedisTemplate;

    @Resource
    private UserInfoMapper userInfoMapper;

    // 注入本身, 产生了循环依赖, 使用懒加载
    @Autowired
    @Lazy
    FileInfoServiceImpl infoService;

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
        if (categoryEnum.getCode() != 0) {
            fileQueryWrapper.eq("file_category", categoryEnum.getCode());
        }
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
        boolean uploadSuccess = true;
        String path = null;
        try {
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
                    updateUserSpace(userId, userSpaceVO.getUseSpace(), null);
                    userInfoMapper.updateUserSpace(userId, userSpaceVO.getUseSpace(), null);
                    return uploadFileVO;
                }
            }

            path = projectFile + CodeConstants.TEMP_FILE + userId + "/" + uploadFileVO.getFileId() + "/";
            // 分片传输数据
            // 判断临时数据是否超过剩余空间
            long curSize = getCurSize(userId, uploadFileVO.getFileId());
            if (curSize + upLoadFileDTO.getFile().getSize() + userSpaceVO.getUseSpace() > userSpaceVO.getTotalSpace()) {
                // TODO: 2024/9/25 断点续传 删除临时文件大小问题
                // 删除临时文件大侠redis数据
                stringRedisTemplate.delete(RedisConstants.MYPAN_FILE_TEMP_SIZE + userId + ":" + uploadFileVO.getFileId());
                throw new AppException(ResponseCodeEnums.CODE_904);
            }
            // 创建临时数据分片存储目录
            File file = new File(path);
            if (!file.exists()) {
                file.mkdirs();
            }
            // 创建临时文件存储路径, 并写入本地
            Path tempPath = Paths.get(path + upLoadFileDTO.getChunkIndex());
            Files.write(tempPath, upLoadFileDTO.getFile().getBytes());
            // 存入临时文件的大小到Redis
            stringRedisTemplate.opsForValue().set(RedisConstants.MYPAN_FILE_TEMP_SIZE + userId + ":" + uploadFileVO.getFileId(),
                    String.valueOf((curSize + upLoadFileDTO.getFile().getSize())), 1, TimeUnit.HOURS);
            // 判断分片数据传输是否完成
            if (upLoadFileDTO.getChunkIndex() < upLoadFileDTO.getChunks() - 1) {
                uploadFileVO.setStatus(UploadStatusEnums.UPLOADING.getCode());
            } else {
                // 最后一片数据，进行分片合并
                String fileSuffix = StringTools.getFileSuffix(upLoadFileDTO.getFileName());
                FileTypeEnums fileTypeEnums = FileTypeEnums.getFileTypeBySuffix("." + fileSuffix);
                String month = new SimpleDateFormat("YYYYMM").format(curDate);
                String dbPath = month + "/" + userId + upLoadFileDTO.getFileId() + "." + fileSuffix;
                if ("图片".equals(fileTypeEnums.getDesc())) {
                    dbPath = month + "/" + userId + upLoadFileDTO.getFileId() + CodeConstants.IMAGE_FILE_SUFFIX;
                }
                // 将FileInfo存入数据库
                FileInfo fileInfo = new FileInfo();
                fileInfo.setUserId(userId);
                fileInfo.setFilePid(upLoadFileDTO.getFilePid());
                fileInfo.setFileName(upLoadFileDTO.getFileName());
                fileInfo.setFileMd5(upLoadFileDTO.getFileMd5());
                fileInfo.setFileId(uploadFileVO.getFileId());
                fileInfo.setFileType(fileTypeEnums.getType());
                fileInfo.setCreateTime(curDate);
                fileInfo.setLastUpdateTime(curDate);
                fileInfo.setDelFlag(FileDelFlagEnums.NORMAL.getCode());
                // 设置文件为转码中，当文件分片合并完毕是修改
                fileInfo.setStatus(FileStatusEnums.TRANSFER.getCode());
                fileInfo.setFolderType(FolderTypeEnums.DOCUMENT.getType());
                fileInfo.setFileCategory(fileTypeEnums.getCategory().getCode());
                fileInfo.setFilePath(dbPath);
                save(fileInfo);
                // 更新用户使用空间
                Long totalUseSize = getCurSize(userId, upLoadFileDTO.getFileId());
                updateUserSpace(userId, totalUseSize, null);
                userInfoMapper.updateUserSpace(userId, totalUseSize, null);
                // 设置上传完成
                uploadFileVO.setStatus(UploadStatusEnums.UPLOAD_FINISH.getCode());
                // 异步开始合并文件，在当前事务提交之后进行
                TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
                    @Override
                    public void afterCommit() {
                        infoService.mergeFiles(fileInfo.getFileId(), fileInfo.getUserId());
                    }
                });

            }
            return uploadFileVO;
        } catch (AppException e) {
            uploadSuccess = false;
            log.error("文件上传失败", e);
            throw e;
        } catch (Exception e) {
            uploadSuccess = false;
            log.error("文件上传失败", e);
            throw new AppException("文件上传失败");
        } finally {
            // 文件未上传成功，删除临时文件
            if (!uploadSuccess) {
                try {
                    FileUtils.deleteDirectory(new File(path));
                } catch (IOException e) {
                    log.error("删除临时文件失败");
                }
            }
        }
    }

    @Override
    public void getImage(HttpServletResponse response, String folder, String fileName) {
        String targetPath = projectFile + CodeConstants.FILE + folder + "/" + fileName;
        if (targetPath.contains("../") || targetPath.contains("..\\")) {
            response.setStatus(HttpServletResponse.SC_NOT_FOUND);
            return;
        }
        File file = new File(targetPath);
        if (!file.exists()) {
            throw new AppException("文件不存在");
        }
        response.setContentType("image/jpeg");
        response.setHeader("Cache-Control", "max-age=2592000");
        try (FileInputStream inputStream = new FileInputStream(file);
             ServletOutputStream outputStream = response.getOutputStream()){
            int len = 0;
            byte[] bytes = new byte[1024];
            while((len = inputStream.read(bytes)) != -1) {
                outputStream.write(bytes, 0, len);
            }
            // 确保刷新并关闭
            outputStream.flush();
        } catch (IOException e) {
            throw new AppException("写入或读取文件失败");
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
    public void getVideoInfo(HttpServletResponse response, String fileId, String token) {
        String targetPath = null;
        if (fileId.contains("../") || fileId.contains("..\\")) {
            response.setStatus(HttpServletResponse.SC_NOT_FOUND);
            return;
        }
        String userId = (String) stringRedisTemplate.opsForHash().entries(RedisConstants.MYPAN_LOGIN_USER_KEY + token).get("userId");
        FileInfo fileInfo = infoService.getOne(new QueryWrapper<FileInfo>().eq("file_id", fileId).eq("user_id", userId));
        if (fileInfo == null) {
            // 请求的是ts文件
            String realFileId = fileId.split("-")[0];
            fileInfo = infoService.getOne(new QueryWrapper<FileInfo>().eq("file_id", realFileId).eq("user_id", userId));
            targetPath = projectFile + CodeConstants.FILE + fileInfo.getFilePath().split("\\.")[0] + File.separator + fileId;
        } else {
            targetPath = projectFile + CodeConstants.FILE + fileInfo.getFilePath().split("\\.")[0] + File.separator + CodeConstants.M3U8_NAME;
        }
        File file = new File(targetPath);
        if (!file.exists()) {
            return;
        }
        try (FileInputStream inputStream = new FileInputStream(file);
             ServletOutputStream outputStream = response.getOutputStream()){
            int len = 0;
            byte[] bytes = new byte[1024];
            while((len = inputStream.read(bytes)) != -1) {
                outputStream.write(bytes, 0, len);
            }
            // 确保刷新并关闭
            outputStream.flush();
        } catch (IOException e) {
            throw new AppException("写入或读取文件失败");
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
            int endIndex = split[split.length - 1].length() + 1;
            return fileName.substring(0, fileName.length() - endIndex) + "(" + count + ")." + split[split.length - 1];
        }
        return fileName;
    }

    private void updateUserSpace(String userId, Long useSpace, Long totalSpace) {
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

    @Async
    protected void mergeFiles(String fileId, String userId) {
        boolean mergeFilesSuccess = true;
        String targetPath = null;
        String cover = null;
        FileInfo fileInfo = null;
        try {
            fileInfo = getOne(new QueryWrapper<FileInfo>().eq("file_id", fileId).eq("user_id", userId));
            if (fileInfo == null || !Objects.equals(fileInfo.getStatus(), FileStatusEnums.TRANSFER.getCode())) {
                return;
            }
            targetPath = projectFile + CodeConstants.FILE + fileInfo.getFilePath().split("/")[0];
            File targetFile = new File(targetPath);
            if (!targetFile.exists()) {
                targetFile.mkdirs();
            }
            String sourcePath = projectFile + CodeConstants.TEMP_FILE + userId + "/" + fileId + "/";
            // 合并分片文件
            union(targetFile, sourcePath, fileInfo.getFilePath().split("/")[1], true);
            // TODO: 2024/9/24 视频切割
            if (Objects.equals(fileInfo.getFileType(), FileTypeEnums.VIDEO.getType())) {
                // 如果是视频文件，需要视频切割
                videoCutting(fileId, userId);
                // 生成视频封面缩略图
                cover = coverThumbnail(targetPath, fileId, userId);
            } else if (Objects.equals(fileInfo.getFileType(), FileTypeEnums.IMAGE.getType())){
                //  todo 是图片，则添加图片缩略图
                cover = coverThumbnail(targetPath, fileId, userId);
            }
        } catch (Exception e) {
            mergeFilesSuccess = false;
            log.error("文件转码失败,文件Id:{},用户Id:{}", fileId,  userId, e);
        } finally {
            //  修改FileInfo状态
            if (fileInfo != null) {
                fileInfo.setFileSize(new File(targetPath + "/" + fileInfo.getFilePath().split("/")[1]).length());
                fileInfo.setFileCover(cover);
                fileInfo.setStatus(mergeFilesSuccess?FileStatusEnums.USING.getCode(): FileStatusEnums.TRANSFER_FAIL.getCode());
                fileInfo.setFileId(fileId);
                fileInfo.setUserId(userId);
                updateById(fileInfo);
            }
        }
    }

    private String coverThumbnail(String filePath, String fileId, String userId) {
        //  ffmpeg -i test.asf -y -f image2 -t 0.001 -s 352x240 a.jpg
        FileInfo fileInfo = getOne(new QueryWrapper<FileInfo>().eq("file_id", fileId).eq("user_id", userId));
        String sourcePath = filePath + "/" + fileInfo.getFilePath().split("/")[1];
        String targetPath = fileInfo.getFilePath().split("\\.")[0] + "_" + CodeConstants.IMAGE_FILE_SUFFIX;
        String targetFileName = filePath + "/" + targetPath.split("/")[1];
        List<String> command = Arrays.asList("ffmpeg", "-i", sourcePath, "-y", "-f", "image2", "-t", "0.001", "-s",
                CodeConstants.RESOLUTION_150, targetFileName);
        ProcessUtils.executeCommand(command);
        return targetPath;
    }

    private void videoCutting(String fileId, String userId) {
        FileInfo fileInfo = getOne(new QueryWrapper<FileInfo>().eq("file_id", fileId).eq("user_id", userId));
        String sourcePath = projectFile + CodeConstants.FILE + fileInfo.getFilePath();
        // ts文件存放路径
        String tsPath = projectFile + CodeConstants.FILE + fileInfo.getFilePath().split("\\.")[0];
        File tsFile = new File(tsPath);
        if (!tsFile.exists()) {
            tsFile.mkdirs();
        }
        // 将mp4 转化为 ts视频文件 "ffmpeg -y -i %s -vcodec copy -acodec copy -bsf:v h264_mp4toannexb %s"
        String tsPathName = tsPath + "/" + CodeConstants.TS_NAME;
        List<String> tsList = Arrays.asList
                ("ffmpeg", "-y", "-i", sourcePath, "-vcodec", "copy", "-acodec", "copy", "-bsf:v", "h264_mp4toannexb", tsPathName);
        // 将ts视频文件，切割成30秒一个的切片  ffmpeg -i %s -c copy -map 0 -f segment -segment-list %s -segment_time 30 %s/%s-%%4d.ts
        String cmdCutTs = "%s"+ File.separator + "%s-%%4d.ts";
        String cutName = String.format(cmdCutTs, tsPath, fileId);
        String m3u8Path = tsPath + File.separator + CodeConstants.M3U8_NAME;
        // TODO: 2024/9/28 视频切割30秒为一个ts会出现无法加载0001bug
        List<String> tsCutList = Arrays.asList(
                "ffmpeg", "-i", tsPathName, "-c", "copy", "-map", "0", "-f", "segment", "-segment_list", m3u8Path, "-segment_time", "20", cutName);
        ProcessUtils.executeCommand(tsList);
        ProcessUtils.executeCommand(tsCutList);
        // 删除index.ts文件
        new File(tsPathName).delete();
    }

    private void union(File targetFile, String sourcePath, String realFileName, boolean delSource) {
        //  targetFile.getPath() + "/" + realFileName  目标文件地址
        try (RandomAccessFile rwFile = new RandomAccessFile(targetFile.getPath() + "/" + realFileName, "rw")) {
            File sourceFile = new File(sourcePath);
            if (!sourceFile.exists()) {
                throw new AppException("文件目录不存在:" + sourceFile.getPath());
            }
            int len = 0;
            byte[] bytes = new byte[1024 * 5];
            for (int i = 0; i < sourceFile.listFiles().length; i++) {
                String source = sourcePath + i;
                try (RandomAccessFile rFile = new RandomAccessFile(source, "r")) {
                    while ((len = rFile.read(bytes)) != -1) {
                        rwFile.write(bytes, 0, len);
                    }
                }
            }
        } catch (FileNotFoundException e) {
            throw new AppException("文件没有找到: " + targetFile.getPath(), e );
        } catch (IOException e) {
            throw new AppException("文件合并失败", e);
        } finally {
            if (delSource) {
                try {
                    FileUtils.deleteDirectory(new File(sourcePath));
                } catch (IOException e) {
                   log.error("删除临时文件失败，文件路径:{}", sourcePath, e);
                }
            }
        }
    }

}
