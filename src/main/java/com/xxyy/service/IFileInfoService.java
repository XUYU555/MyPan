package com.xxyy.service;

import com.baomidou.mybatisplus.extension.service.IService;
import com.xxyy.entity.FileInfo;
import com.xxyy.entity.dto.*;
import com.xxyy.entity.vo.FileInfoVO;
import com.xxyy.entity.vo.FolderInfoVO;
import com.xxyy.entity.vo.PagingQueryVO;
import com.xxyy.entity.vo.UploadFileVO;

import javax.servlet.http.HttpServletResponse;
import java.util.List;

/**
 * @author xy
 * @date 2024-09-22 11:05
 */
public interface IFileInfoService extends IService<FileInfo> {

    PagingQueryVO<FileInfoVO> pageQueryFile(FileQueryDTO fileQueryDTO, String token);

    UploadFileVO uploadFile(UploadFileDTO upLoadFileDTO, String token);

    String getImage(HttpServletResponse response, String folder, String fileName);

    void getFile(HttpServletResponse response, String fileId, String token);

    FileInfoVO createFolder(String filePid, String folderName, String token);

    List<FolderInfoVO> getFolderInfo(String path, String token);

    FileInfoVO fileRename(String token, String fileId, String fileName);

    List<FileInfoVO> getAllFolder(String token, String filePid, String currentFileIds);

    void changeFileFolder(String token, String fileIds, String filePid);

    String createDownloadUrl(String fileId, String token);

    void downloadFile(String code, HttpServletResponse response);

    void removeFile2RecycleBatch(String fileIds, String token);

}
