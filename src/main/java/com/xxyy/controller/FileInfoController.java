package com.xxyy.controller;

import com.xxyy.annotation.GlobalInterceptor;
import com.xxyy.dto.FileQueryDTO;
import com.xxyy.dto.PagingQueryVO;
import com.xxyy.dto.UploadFileDTO;
import com.xxyy.dto.UploadFileVO;
import com.xxyy.service.IFileInfoService;
import com.xxyy.utils.common.Result;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.*;

import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

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
    @GlobalInterceptor(checkParams = true)
    public Result<UploadFileVO> upLoadFile(@ModelAttribute UploadFileDTO upLoadFileDTO, HttpServletRequest request) {
        String token = request.getHeader("authorization");
        UploadFileVO upLoadFileVO = fileService.uploadFile(upLoadFileDTO, token);
        return Result.data(upLoadFileVO);
    }

    @GetMapping(value = "/getImage/{imageFolder}/{imageName}")
    public void getImage(HttpServletResponse response, @PathVariable(name = "imageFolder")String folder,
                         @PathVariable(name = "imageName")String fileName) {
        fileService.getImage(response, folder, fileName);
    }

}
