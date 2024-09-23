package com.xxyy.controller;

import com.xxyy.annotation.GlobalInterceptor;
import com.xxyy.dto.FileQueryDTO;
import com.xxyy.dto.PagingQueryVO;
import com.xxyy.dto.UploadFileDTO;
import com.xxyy.dto.UploadFileVO;
import com.xxyy.service.IFileInfoService;
import com.xxyy.utils.common.Result;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import javax.servlet.http.HttpServletRequest;

/**
 * @author xy
 * @date 2024-09-22 11:17
 */

@RestController(value = "fileRestController")
@RequestMapping(value = "/file")
public class FileInfoController {

    @Autowired
    IFileInfoService fileService;


    @PostMapping(value = "/loadDataList")
    @GlobalInterceptor(checkParams = true)
    public Result<PagingQueryVO> loadDataList(FileQueryDTO fileQueryDTO, HttpServletRequest request) {
        PagingQueryVO output = fileService.pageQueryFile(fileQueryDTO, request.getHeader("authorization"));
        return Result.data(output);
    }

    @PostMapping(value = "/uploadFile")
    // @GlobalInterceptor(checkParams = true)
    public Result<UploadFileVO> upLoadFile(@ModelAttribute UploadFileDTO upLoadFileDTO, HttpServletRequest request) {
        String token = request.getHeader("authorization");
        UploadFileVO upLoadFileVO = fileService.uploadFile(upLoadFileDTO, token);
        return Result.data(upLoadFileVO);
    }

}
