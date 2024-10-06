package com.xxyy.controller;

import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.xxyy.annotation.GlobalInterceptor;
import com.xxyy.annotation.VerifyParams;
import com.xxyy.entity.FileInfo;
import com.xxyy.entity.FileShare;
import com.xxyy.entity.vo.FileShareVO;
import com.xxyy.entity.vo.PagingQueryVO;
import com.xxyy.service.IFileShareService;
import com.xxyy.utils.StringTools;
import com.xxyy.utils.common.Result;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import javax.servlet.http.HttpServletRequest;

/**
 * @author xy
 * @date 2024-10-05 19:06
 */

@RestController
@RequestMapping(value = "/share")
public class FileShareController {

    @Autowired
    IFileShareService fileShareService;

    @PostMapping(value = "/loadShareList")
    @GlobalInterceptor
    public Result<PagingQueryVO<FileShareVO>> pageShareList(HttpServletRequest request, String pageNo, String pageSize) {
        String token = request.getHeader("authorization");
        Page<FileShareVO> fileSharePage = new Page<>();
        fileSharePage.setSize(!StringTools.isEmpty(pageSize) ? Long.parseLong(pageSize): 15);
        fileSharePage.setCurrent(!StringTools.isEmpty(pageNo)? Long.parseLong(pageNo): 1);
        return Result.data(fileShareService.pageShareList(token, fileSharePage));
    }

    @PostMapping(value = "/shareFile")
    @GlobalInterceptor
    public Result<FileShareVO> shareFile(HttpServletRequest request, @VerifyParams(required = true) String fileId,
                                         @VerifyParams(required = true) Integer validType, String code) {
        String token = request.getHeader("authorization");
        return Result.data(fileShareService.shareFile(token, fileId, validType, code)) ;
    }

    @PostMapping(value = "/cancelShare")
    @GlobalInterceptor(checkParams = true)
    public Result<?> cancelShare(HttpServletRequest request, @VerifyParams(required = true) String shareIds) {
        String token = request.getHeader("authorization");
        fileShareService.cancelShare(token, shareIds);
        return Result.ok();
    }

}
