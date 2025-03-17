package com.xxyy.entity.dto;

import com.xxyy.annotation.VerifyParams;
import lombok.Data;
import org.springframework.web.multipart.MultipartFile;

/**
 * @author xy
 * @date 2024-09-22 22:24
 */

@Data
public class UploadFileDTO {

    /**
     * 文件Id
     */
    private String fileId;

    /**
     * 文件名
     */
    @VerifyParams(required = true)
    private String fileName;


    @VerifyParams(required = true)
    private long fileSize;

    /**
     * 父级id
     */
    @VerifyParams(required = true)
    private String filePid;

    /**
     * 文件MD5值（判断秒传）
     */
    @VerifyParams(required = true)
    private String fileMd5;

    /**
     * 当前分片索引
     */
    @VerifyParams(required = true)
    private int chunkIndex;

    /**
     * 分片总数
     */
    @VerifyParams(required = true)
    private int chunks;

}
