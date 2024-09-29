package com.xxyy.entity.vo;

import com.xxyy.entity.FileInfo;
import lombok.Data;

/**
 * @author xy
 * @date 2024-09-29 13:33
 */

@Data
public class FolderInfoVO {

    private String fileName;

    private String fileId;

    public static FolderInfoVO of(FileInfo fileInfo) {
        FolderInfoVO folderInfoVO = new FolderInfoVO();
        folderInfoVO.fileId = fileInfo.getFileId();
        folderInfoVO.fileName = fileInfo.getFileName();
        return folderInfoVO;
    }
}
