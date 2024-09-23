package com.xxyy.dto;

import com.xxyy.entity.FileInfo;
import lombok.Data;

import java.util.Date;

/**
 * @author xy
 * @date 2024-09-22 11:23
 */

@Data
public class FileInfoVO {

    private String fileId;

    private String filePid;

    private Long fileSize;

    private String fileName;

    private String fileCover;

    private Date createTime;

    private Date lastUpdateTime;

    private int folderType;

    private int fileType;

    private int status;

    public static FileInfoVO of(FileInfo fileInfo) {
        FileInfoVO fileInfoVO = new FileInfoVO();
        fileInfoVO.fileId = fileInfo.getFileId();
        fileInfoVO.filePid = fileInfo.getFilePid();
        fileInfoVO.fileSize = fileInfo.getFileSize();
        fileInfoVO.fileName = fileInfo.getFileName();
        fileInfoVO.fileCover = fileInfo.getFileCover();
        fileInfoVO.createTime = fileInfo.getCreateTime();
        fileInfoVO.lastUpdateTime = fileInfo.getLastUpdateTime();
        fileInfoVO.folderType = fileInfo.getFolderType();
        fileInfoVO.fileType = fileInfo.getFileType();
        fileInfoVO.status = fileInfo.getStatus();
        return fileInfoVO;
    }
}
