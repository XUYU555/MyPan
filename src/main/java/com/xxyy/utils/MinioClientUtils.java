package com.xxyy.utils;

import com.xxyy.entity.enums.ResponseCodeEnums;
import com.xxyy.utils.common.AppException;
import com.xxyy.utils.common.CustomMinioClient;
import io.minio.BucketExistsArgs;
import io.minio.MakeBucketArgs;
import io.minio.PutObjectArgs;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import javax.annotation.Resource;
import java.io.File;
import java.io.FileInputStream;
import java.net.URLConnection;


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
                    .contentType(contentType)
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

}
