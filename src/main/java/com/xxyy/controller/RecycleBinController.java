package com.xxyy.controller;

import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.xxyy.annotation.GlobalInterceptor;
import com.xxyy.annotation.VerifyParams;
import com.xxyy.entity.FileInfo;
import com.xxyy.entity.vo.FileInfoVO;
import com.xxyy.entity.vo.PagingQueryVO;
import com.xxyy.service.IRecycleBinService;
import com.xxyy.utils.StringTools;
import com.xxyy.utils.common.Result;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

/**
 * @author xy
 * @date 2024-10-02 20:36
 */

@RestController
@RequestMapping(value = "/recycle")
public class RecycleBinController {

    @Autowired
    IRecycleBinService recycleBinService;

    @PostMapping(value = "/loadRecycleList")
    @GlobalInterceptor
    public Result<PagingQueryVO<FileInfoVO>> getRecycleList(HttpServletRequest request, String pageSize, String pageNo) {
        String token = request.getHeader("authorization");
        Page<FileInfo> fileInfoPage = new Page<>();
        fileInfoPage.setSize(!StringTools.isEmpty(pageSize) ? Long.parseLong(pageSize): 15);
        fileInfoPage.setCurrent(!StringTools.isEmpty(pageNo)? Long.parseLong(pageNo): 1);
        return Result.data(recycleBinService.getRecycleList(fileInfoPage, token));
    }

    @PostMapping(value = "/recoverFile")
    @GlobalInterceptor(checkParams = true)
    public Result<?> recoverFile(HttpServletRequest request, @VerifyParams(required = true) String fileIds) {
        String token = request.getHeader("authorization");
        recycleBinService.recoverFile(token, fileIds);
        return Result.ok();
    }

    @PostMapping(value = "/delFile")
    @GlobalInterceptor(checkParams = true)
    public Result<?> deleteFile(HttpServletRequest request, @VerifyParams(required = true) String fileIds) {
        String token = request.getHeader("authorization");
        recycleBinService.deleteFile(token, fileIds);
        return Result.ok();
    }

}
