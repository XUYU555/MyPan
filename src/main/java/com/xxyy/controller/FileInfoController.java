package com.xxyy.controller;

import com.xxyy.annotation.GlobalInterceptor;
import com.xxyy.annotation.VerifyParams;
import com.xxyy.entity.dto.*;
import com.xxyy.entity.vo.FileInfoVO;
import com.xxyy.entity.vo.FolderInfoVO;
import com.xxyy.entity.vo.PagingQueryVO;
import com.xxyy.entity.vo.UploadFileVO;
import com.xxyy.service.IFileInfoService;
import com.xxyy.utils.common.Result;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.*;

import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import java.util.List;

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
    public Result<PagingQueryVO<FileInfoVO>> loadDataList(FileQueryDTO fileQueryDTO, HttpServletRequest request) {
        PagingQueryVO<FileInfoVO> output = fileService.pageQueryFile(fileQueryDTO, request.getHeader("authorization"));
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

    @GetMapping(value = "/ts/getVideoInfo/{fileId}")
    @GlobalInterceptor(checkParams = true)
    public void getVideoInfo(HttpServletResponse response,
                             @VerifyParams(required = true) @PathVariable(name = "fileId")String fileId, HttpServletRequest request) {
        String token = request.getHeader("authorization");
        fileService.getFile(response, fileId, token);
    }

    @RequestMapping(value = "/getFile/{fileId}")
    @GlobalInterceptor(checkParams = true)
    public void getFile(HttpServletResponse response,
                        @VerifyParams(required = true)@PathVariable(name = "fileId")String fileId, HttpServletRequest request) {
        String token = request.getHeader("authorization");
        fileService.getFile(response, fileId, token);
    }

    @RequestMapping(value = "/getFile/{fileId}/{token}")
    public void getPdfFile(HttpServletResponse response, @PathVariable(name = "fileId")String fileId, @PathVariable(name = "token")String token) {
        fileService.getFile(response, fileId, token);
    }

    @PostMapping(value = "/newFoloder")
    @GlobalInterceptor(checkParams = true)
    public Result<FileInfoVO> createFolder(
            @VerifyParams(required = true) String filePid, @VerifyParams(required = true) String fileName, HttpServletRequest request) {
        String token = request.getHeader("authorization");
        FileInfoVO folder = fileService.createFolder(filePid, fileName, token);
        return Result.data(folder);
    }

    @PostMapping(value = "/getFolderInfo")
    @GlobalInterceptor(checkParams = true)
    public Result<List<FolderInfoVO>> getFolderInfo(@VerifyParams(required = true) String path, HttpServletRequest request) {
        String token = request.getHeader("authorization");
        List<FolderInfoVO> list = fileService.getFolderInfo(path, token);
        return Result.data(list);
    }

    @PostMapping(value = "/rename")
    @GlobalInterceptor(checkParams = true)
    public Result<FileInfoVO> fileRename(HttpServletRequest request
            , @VerifyParams(required = true) String fileId, @VerifyParams(required = true) String fileName ) {
        String token = request.getHeader("authorization");
        return Result.data(fileService.fileRename(token, fileId, fileName));
    }

    @PostMapping(value = "/loadAllFolder")
    @GlobalInterceptor(checkParams = true)
    public Result<List<FileInfoVO>> getAllFolder(HttpServletRequest request, @VerifyParams(required = true) String filePid,
                                                 @VerifyParams(required = true) String currentFileIds) {
        String token = request.getHeader("authorization");
        return Result.data(fileService.getAllFolder(token, filePid, currentFileIds));
    }

    @PostMapping(value = "/changeFileFolder")
    @GlobalInterceptor(checkParams = true)
    public Result<?> changeFileFolder(HttpServletRequest request, @VerifyParams(required = true) String fileIds,
                                                 @VerifyParams(required = true) String filePid) {
        String token = request.getHeader("authorization");
        fileService.changeFileFolder(token, fileIds, filePid);
        return Result.ok();
    }

    @PostMapping(value = "/createDownloadUrl/{fileId}")
    @GlobalInterceptor(checkParams = true)
    public Result<String> createDownloadUrl(@PathVariable(name = "fileId")@VerifyParams(required = true) String fileId, HttpServletRequest request) {
        String token = request.getHeader("authorization");
        return Result.data(fileService.createDownloadUrl(fileId, token));
    }

    @GetMapping(value = "/download/{code}")
    @GlobalInterceptor(checkParams = true, checkLoing = false)
    public void downloadFile(@PathVariable(name = "code")@VerifyParams(required = true) String code, HttpServletResponse response) {
        fileService.downloadFile(code, response);
    }

    @PostMapping(value = "/delFile")
    @GlobalInterceptor(checkParams = true)
    public Result<?> delFile(@VerifyParams(required = true) String fileIds, HttpServletRequest request) {
        String token = request.getHeader("authorization");
        fileService.removeFile2RecycleBatch(fileIds, token);
        return Result.ok();
    }


}
