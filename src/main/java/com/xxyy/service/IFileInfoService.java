package com.xxyy.service;

import com.baomidou.mybatisplus.extension.service.IService;
import com.xxyy.dto.FileQueryDTO;
import com.xxyy.dto.PagingQueryVO;
import com.xxyy.dto.UploadFileDTO;
import com.xxyy.dto.UploadFileVO;
import com.xxyy.entity.FileInfo;

import javax.servlet.http.HttpServletResponse;

/**
 * @author xy
 * @date 2024-09-22 11:05
 */
public interface IFileInfoService extends IService<FileInfo> {

    PagingQueryVO pageQueryFile(FileQueryDTO fileQueryDTO, String token);

    UploadFileVO uploadFile(UploadFileDTO upLoadFileDTO, String token);

    void getImage(HttpServletResponse response, String folder, String fileName);

    void getVideoInfo(HttpServletResponse response, String fileId, String token);
}
