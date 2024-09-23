package com.xxyy.dto;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * @author xy
 * @date 2024-09-22 22:30
 */

@Data
@AllArgsConstructor
@NoArgsConstructor
public class UploadFileVO {

    /**
     * 文件id
     */
    private String fileId;

    /**
     * 返回给前端，来控制是否继续上传分片
     */
    private String status;

}
