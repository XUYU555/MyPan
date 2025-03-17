package com.xxyy.utils;

import com.xxyy.entity.dto.UploadFileDTO;
import com.xxyy.entity.enums.ResponseCodeEnums;
import com.xxyy.utils.common.AppException;
import com.xxyy.utils.common.CustomMinioClient;
import io.minio.*;
import io.minio.http.Method;
import io.minio.messages.DeleteError;
import io.minio.messages.DeleteObject;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import javax.annotation.Resource;
import java.io.File;
import java.io.FileInputStream;
import java.io.InputStream;
import java.net.URLConnection;
import java.util.HashMap;
import java.util.List;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;
import java.util.stream.Stream;


/**
 * @author xy
 * @date 2024-12-18 17:50
 */
@Component
@Slf4j
public class MinioClientUtils {

    @Value("${minio.bucket}")
    private String bucket;

    @Resource
    private CustomMinioClient customMinioClient;

    public void uploadFile(String targetFile, String filePath) throws Exception {
        if (!customMinioClient.bucketExists(BucketExistsArgs.builder().bucket(bucket).build())) {
            log.info("创建桶：{}", bucket);
            customMinioClient.makeBucket(MakeBucketArgs.builder().bucket(bucket).build());
        }
        File file = new File(targetFile + "/" + filePath.split("/")[1]);
        String contentType = URLConnection.guessContentTypeFromName(file.getName());
        try(FileInputStream inputStream = new FileInputStream(file)) {
            customMinioClient.putObject(PutObjectArgs.builder()
                    .object(filePath)
                    .bucket(bucket)
                    .stream(inputStream, file.length(), -1)
                    .contentType(contentType == null? "application/octet-stream": contentType)
                    .build());
        } catch (Exception e) {
            log.error("文件：{} 上传失败", file.getPath(), e);
            throw new AppException(ResponseCodeEnums.CODE_500);
        }
    }

    public void uploadM3U8File(File sourceFile, String objectName) throws Exception {
        if (!customMinioClient.bucketExists(BucketExistsArgs.builder().bucket(bucket).build())) {
            log.info("创建桶：{}", bucket);
            customMinioClient.makeBucket(MakeBucketArgs.builder().bucket(bucket).build());
        }
        String contentType = contentTypeFromName(sourceFile.getName());
        try(FileInputStream inputStream = new FileInputStream(sourceFile)) {
            customMinioClient.putObject(PutObjectArgs.builder()
                    .object(objectName)
                    .bucket(bucket)
                    .stream(inputStream, sourceFile.length(), -1)
                    .contentType(contentType)
                    .build());
        } catch (Exception e) {
            log.error("切片文件：{} 上传失败", sourceFile.getPath(), e);
            throw new AppException(ResponseCodeEnums.CODE_500);
        }
    }

    public String getPresignedUrl(String targetPath) throws Exception {
        if (!customMinioClient.bucketExists(BucketExistsArgs.builder().bucket(bucket).build())) {
            log.info("创建桶：{}", bucket);
            customMinioClient.makeBucket(MakeBucketArgs.builder().bucket(bucket).build());
        }
        return customMinioClient.getPresignedObjectUrl(
                GetPresignedObjectUrlArgs.builder()
                .bucket(bucket)
                .object(targetPath)
                .method(Method.GET)
                .expiry(6, TimeUnit.HOURS)
                .build());
    }

    private String contentTypeFromName(String fileName) {
        String extend = fileName.split("\\.")[1];
        if("ts".equals(extend)) {
            return "video/MP2T";
        }
        if("m3u8".equals(extend)) {
            return "application/vnd.apple.mpegurl";
        }
        return "application/octet-stream";
    }

    public InputStream downloadVideoFile(String filePath) throws Exception {
        GetObjectResponse response = customMinioClient.getObject(GetObjectArgs.builder()
                .bucket(bucket)
                .object(filePath)
                .build());
        if(response == null) {
            throw new AppException(ResponseCodeEnums.CODE_500);
        }
        InputStream fileInputStream = response;
        return fileInputStream;
    }

    public String createDownloadUrl(String filePath, String fileName) throws Exception {
        HashMap<String, String> map = new HashMap<>();
        map.put("response-content-disposition", "attachment; filename=" + fileName);
        String presignedObjectUrl = customMinioClient.getPresignedObjectUrl(GetPresignedObjectUrlArgs.builder()
                .bucket(bucket)
                .object(filePath)
                .expiry(5, TimeUnit.MINUTES)
                .method(Method.GET)
                .extraQueryParams(map)
                .build());
        return presignedObjectUrl;
    }

    public String getPartPresignedURL(String fileId, int chunkIndex) throws Exception{
        if (!customMinioClient.bucketExists(BucketExistsArgs.builder().bucket(bucket).build())) {
            log.info("创建桶：{}", bucket);
            customMinioClient.makeBucket(MakeBucketArgs.builder().bucket(bucket).build());
        }
        String objectPath = "temp/" + fileId + "/chunk-" + chunkIndex;
        return customMinioClient.getPresignedObjectUrl(
                GetPresignedObjectUrlArgs.builder()
                        .bucket(bucket)
                        .object(objectPath)
                        .method(Method.PUT)
                        .expiry(1, TimeUnit.HOURS)
                        .build());
    }

    public void mergeShardFile(UploadFileDTO uploadFileDTO, String dbPath) throws Exception {
        if (!customMinioClient.bucketExists(BucketExistsArgs.builder().bucket(bucket).build())) {
            log.info("创建桶：{}", bucket);
            customMinioClient.makeBucket(MakeBucketArgs.builder().bucket(bucket).build());
        }
        String objectPath = "temp/" + uploadFileDTO.getFileId() + "/chunk-";
        List<ComposeSource> composeSources = Stream.iterate(0, i -> ++i)
                .limit(uploadFileDTO.getChunks())
                .map(i -> ComposeSource.builder()
                        .bucket(bucket)
                        .object(objectPath + i)
                        .build())
                .collect(Collectors.toList());
        ObjectWriteResponse objectWriteResponse = customMinioClient.composeObject(ComposeObjectArgs.builder()
                .bucket(bucket)
                .object(dbPath)
                .sources(composeSources)
                .build());
        // 合并完成需要删除分片文件
        Iterable<Result<DeleteError>> results = customMinioClient.removeObjects(RemoveObjectsArgs.builder()
                .bucket(bucket)
                .objects(Stream.iterate(0, i -> ++i)
                        .limit(uploadFileDTO.getChunks())
                        .map(i -> new DeleteObject(objectPath + i))
                        .collect(Collectors.toList()))
                .build());
        // 可以使用返回值results查看是否删除失败
        results.forEach(f -> {
            try {
                DeleteError deleteError = f.get();
            } catch (Exception e) {
                log.error("分片文件删除失败", e);
            }
        });
    }
}
