package com.xxyy.entity.vo;

import com.xxyy.entity.FileInfo;
import com.xxyy.entity.FileShare;
import lombok.Data;

import java.text.SimpleDateFormat;
import java.util.Date;

/**
 * @author xy
 * @date 2024-10-05 19:13
 */

@Data
public class FileShareVO {

    private String shareId;

    private String fileId;

    private String userId;

    private String shareTime;

    private String expireTime;

    private Integer type;

    private String code;

    private Integer showCount;

    private String fileName;

    private Integer folderType;

    private Integer fileCategory;

    private Integer fileType;

    private String fileCover;

    public static FileShareVO of(FileShare fileShare, FileInfo fileInfo) {
        SimpleDateFormat simpleDateFormat = new SimpleDateFormat("yyyy-MM-dd");
        FileShareVO  shareVO = new FileShareVO();
        shareVO.shareId = fileShare.getShareId();
        shareVO.fileId = fileShare.getFileId();
        shareVO.userId = fileShare.getUserId();
        shareVO.shareTime = simpleDateFormat.format(fileShare.getShareTime());
        shareVO.expireTime = simpleDateFormat.format(fileShare.getExpireTime());
        shareVO.type = fileShare.getType();
        shareVO.code = fileShare.getCode();
        shareVO.showCount = fileShare.getShowCount();
        if (fileInfo != null) {
            shareVO.fileName = fileInfo.getFileName();
            shareVO.folderType = fileInfo.getFolderType();
            shareVO.fileCategory = fileInfo.getFileCategory();
            shareVO.fileType = fileInfo.getFileType();
            shareVO.fileCover = fileInfo.getFileCover();
        }
        return shareVO;
    }

}
