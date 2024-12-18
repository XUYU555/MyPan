package com.xxyy.config;

import com.xxyy.utils.common.CustomMinioClient;
import io.minio.MinioClient;
import lombok.Data;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * @author xy
 * @date 2024-12-18 10:02
 */

@Configuration
public class MinioClientConfig {

    @Value("${minio.url}")
    private String url;

    @Value("${minio.access-key}")
    private String accessKey;

    @Value("${minio.secret-key}")
    private String secretKye;

    @Bean
    public CustomMinioClient minioClient() {
        MinioClient minioClient = MinioClient.builder()
                .endpoint(url)
                .credentials(accessKey, secretKye)
                .build();
        return new CustomMinioClient(minioClient);
    }

}
