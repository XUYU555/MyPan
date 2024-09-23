package com.xxyy.dto;

import com.xxyy.annotation.VerifyParams;
import lombok.Data;

/**
 * @author xy
 * @date 2024-09-22 11:35
 */

@Data
public class FileQueryDTO {

    @VerifyParams(required = true)
    private String category;

    @VerifyParams(required = true)
    private String filePid;

    private String fileNameFuzzy;

    private String pageNo;

    private String pageSize;

}
