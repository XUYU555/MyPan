package com.xxyy.entity.vo;

import com.xxyy.entity.FileInfo;
import com.xxyy.entity.enums.FolderTypeEnums;
import lombok.Data;

import java.text.SimpleDateFormat;

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

    private String createTime;

    private String lastUpdateTime;

    private int folderType;

    private int fileType;

    private int status;

    private int fileCategory;

    // admin使用联合查询才会返回
    private String nickName;

    // admin使用联合查询
    private String userId;

    public static FileInfoVO of(FileInfo fileInfo) {
        SimpleDateFormat simpleDateFormat = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss");
        FileInfoVO fileInfoVO = new FileInfoVO();
        if (fileInfo.getFolderType().intValue() == FolderTypeEnums.DOCUMENT.getType().intValue()) {
            // 是文件
            fileInfoVO.fileId = fileInfo.getFileId();
            fileInfoVO.filePid = fileInfo.getFilePid();
            fileInfoVO.fileSize = fileInfo.getFileSize();
            fileInfoVO.fileName = fileInfo.getFileName();
            fileInfoVO.fileCover = fileInfo.getFileCover();
            fileInfoVO.createTime = simpleDateFormat.format(fileInfo.getCreateTime());
            fileInfoVO.lastUpdateTime = simpleDateFormat.format(fileInfo.getLastUpdateTime());
            fileInfoVO.folderType = fileInfo.getFolderType();
            fileInfoVO.fileType = fileInfo.getFileType();
            fileInfoVO.status = fileInfo.getStatus();
            fileInfoVO.fileCategory = fileInfo.getFileCategory();
        } else {
            // 是目录
            fileInfoVO.fileId = fileInfo.getFileId();
            fileInfoVO.filePid = fileInfo.getFilePid();
            fileInfoVO.fileName = fileInfo.getFileName();
            fileInfoVO.createTime = simpleDateFormat.format(fileInfo.getCreateTime());
            fileInfoVO.lastUpdateTime = simpleDateFormat.format(fileInfo.getLastUpdateTime());
            fileInfoVO.folderType = fileInfo.getFolderType();
            fileInfoVO.status = fileInfo.getStatus();
        }
        return fileInfoVO;
    }
}
