package com.xxyy.controller;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.github.yulichang.wrapper.MPJLambdaWrapper;
import com.xxyy.annotation.GlobalInterceptor;
import com.xxyy.annotation.VerifyParams;
import com.xxyy.entity.FileInfo;
import com.xxyy.entity.FileShare;
import com.xxyy.entity.UserInfo;
import com.xxyy.entity.enums.FileDelFlagEnums;
import com.xxyy.entity.enums.ResponseCodeEnums;
import com.xxyy.entity.vo.*;
import com.xxyy.service.impl.FileInfoServiceImpl;
import com.xxyy.service.impl.FileShareServiceImpl;
import com.xxyy.utils.CodeConstants;
import com.xxyy.utils.RedisConstants;
import com.xxyy.utils.StringTools;
import com.xxyy.utils.common.AppException;
import com.xxyy.utils.common.Result;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.*;

import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import javax.servlet.http.HttpSession;
import java.util.Arrays;
import java.util.Date;
import java.util.List;
import java.util.stream.Collectors;

/**
 * @author xy
 * @date 2024-10-08 14:07
 */

@RestController
@RequestMapping(value = "/showShare")
public class WebShareController {

    @Autowired
    StringRedisTemplate stringRedisTemplate;

    @Autowired
    FileShareServiceImpl fileShareService;

    @Autowired
    FileInfoServiceImpl fileInfoService;


    /**
     * 已经数据提取码之后的获取分享文件信息接口
     * @param session
     * @param request
     * @param shareId
     * @return
     */
    @PostMapping(value = "/getShareLoginInfo")
    @GlobalInterceptor(checkParams = true, checkLoing = false)
    public Result<WebShareInfoVO> getShareLoginInfo(HttpSession session , HttpServletRequest request, @VerifyParams(required = true) String shareId) {
        // 判断是否使用过 校验码 来获得分享文件
        String shareKey = (String) session.getAttribute(CodeConstants.SESSION_SHARE_KEY);
        if (shareKey == null || !shareKey.equals(shareId)) {
            return Result.data(null);
        }
        String token = request.getHeader("authorization");
        String userId = (String) stringRedisTemplate.opsForHash().entries(RedisConstants.MYPAN_LOGIN_USER_KEY + token).get("userId");
        // 根据ShareId获得 分享文件信息
        WebShareInfoVO webShareInfoVO = getWebShareInfoById(shareId);
        if (webShareInfoVO == null || (webShareInfoVO.getExpireTime() != null && new Date().after(webShareInfoVO.getExpireTime()))) {
            throw new AppException(ResponseCodeEnums.CODE_902.getMsg());
        }
        // 判断是不是当前登录用户分享
        if (webShareInfoVO.getUserId().equals(userId)) {
            webShareInfoVO.setCurrentUser(true);
        }
        return Result.data(webShareInfoVO);
    }

    /**
     * 没有输入提取码之前的获取分享文件信息接口
     * @param shareId
     * @return
     */
    @PostMapping(value = "/getShareInfo")
    @GlobalInterceptor(checkParams = true, checkLoing = false)
    public Result<WebShareInfoVO> getShareInfo(@VerifyParams(required = true) String shareId) {
        WebShareInfoVO webShareInfoVO = getWebShareInfoById(shareId);
        if (webShareInfoVO == null || (webShareInfoVO.getExpireTime() != null && new Date().after(webShareInfoVO.getExpireTime()))) {
            throw new AppException(ResponseCodeEnums.CODE_902.getMsg());
        }
        return Result.data(webShareInfoVO);
    }

    @PostMapping(value = "/checkShareCode")
    @GlobalInterceptor(checkParams = true, checkLoing = false)
    @Transactional(rollbackFor = Exception.class)
    public Result<?> checkShareCode(HttpSession session, @VerifyParams(required = true) String shareId,
                                    @VerifyParams(required = true) String code) {
        FileShare fileShare = fileShareService.getOne(new LambdaQueryWrapper<FileShare>()
                .eq(FileShare::getShareId, shareId).eq(FileShare::getCode, code));
        if (fileShare == null || (fileShare.getExpireTime() != null && new Date().after(fileShare.getExpireTime()))) {
            throw new AppException(ResponseCodeEnums.CODE_902.getMsg());
        }
        fileShareService.getBaseMapper().increaseShowCount(shareId);
        // 添加标记，表示已经使用过提取码获取文件信息
        session.setAttribute(CodeConstants.SESSION_SHARE_KEY, shareId);
        return Result.ok();
    }

    /**
     * 获取分享文件链表
     * @param shareId
     * @param filePid
     * @param pageSize
     * @param pageNo
     * @return
     */
    @PostMapping(value = "/loadFileList")
    @GlobalInterceptor(checkLoing = false, checkParams = true)
    public Result<PagingQueryVO<FileInfoVO>> pageFileList(@VerifyParams(required = true) String shareId,@VerifyParams(required = true) String filePid,
                                                          String pageSize, String pageNo) {
        Page<FileInfo> fileInfoPage = new Page<>();
        fileInfoPage.setSize(StringTools.isEmpty(pageSize)? 15: Long.parseLong(pageSize));
        fileInfoPage.setCurrent(StringTools.isEmpty(pageNo)? 1: Long.parseLong(pageNo));
        FileShare fileShare = fileShareService.getOne(new LambdaQueryWrapper<FileShare>().eq(FileShare::getShareId, shareId));
        if (fileShare == null || (fileShare.getExpireTime() != null && new Date().after(fileShare.getExpireTime()))) {
            throw new AppException(ResponseCodeEnums.CODE_902.getMsg());
        }
        // 手动查询文件pid，防止越级访问套取文件信息
        FileInfo fileInfo = fileInfoService.getOne(new LambdaQueryWrapper<FileInfo>().eq(FileInfo::getFileId, fileShare.getFileId())
                .eq(FileInfo::getUserId, fileShare.getUserId()).eq(FileInfo::getDelFlag, FileDelFlagEnums.NORMAL.getCode()));
        if (CodeConstants.ZERO_STR.equals(filePid)) {
            // 第一次来查询文件
            filePid = fileInfo.getFilePid();
        } else {
            // 如果传输了filePid 则需要校验是否越级
            if (!fileInfoService.checkFilePid(filePid, fileInfo.getFileId(), fileInfo.getUserId())) {
                throw new AppException(ResponseCodeEnums.CODE_600);
            }
        }
        Page<FileInfo> infoPage = fileInfoService.page(fileInfoPage, new LambdaQueryWrapper<FileInfo>()
                .eq(filePid.equals(fileInfo.getFilePid()), FileInfo::getFileId, fileShare.getFileId())
                .eq(FileInfo::getUserId, fileShare.getUserId())
                .eq(FileInfo::getFilePid, filePid)
                .eq(FileInfo::getDelFlag, FileDelFlagEnums.NORMAL.getCode()));
        return Result.data(PagingQueryVO.of(infoPage));
    }

    @PostMapping(value = "/getFolderInfo")
    @GlobalInterceptor(checkParams = true, checkLoing = false)
    public Result<List<FolderInfoVO>> getFolderInfo(@VerifyParams(required = true) String path, @VerifyParams(required = true) String shareId) {
        FileShare fileShare = fileShareService.getBaseMapper().selectById(shareId);
        if (fileShare == null) {
            throw new AppException(ResponseCodeEnums.CODE_600);
        }
        List<FolderInfoVO> folderInfo = fileInfoService.getFolderInfo(path, fileShare.getUserId());
        return Result.data(folderInfo);
    }

    @PostMapping(value = "/getFile/{shareId}/{fileId}")
    @GlobalInterceptor(checkParams = true , checkLoing = false)
    public Result<?> getFile(@PathVariable(value = "shareId") @VerifyParams(required = true) String shareId,
                             @PathVariable(value = "fileId") @VerifyParams(required = true) String fileId,
                             HttpServletResponse response) {
        FileShare fileShare = fileShareService.getBaseMapper().selectById(shareId);
        if (fileShare == null) {
            throw new AppException(ResponseCodeEnums.CODE_600);
        }
        fileInfoService.getFile(response, fileId, fileShare.getUserId());
        return Result.ok();
    }

    @GetMapping(value = "/ts/getVideoInfo/{shareId}/{fileId}")
    @GlobalInterceptor(checkParams = true, checkLoing = false)
    public Result<?> getVideoInfo(@PathVariable(value = "shareId") @VerifyParams(required = true) String shareId,
                                  @PathVariable(value = "fileId") @VerifyParams(required = true) String fileId,
                                  HttpServletResponse response) {
        FileShare fileShare = fileShareService.getBaseMapper().selectById(shareId);
        if (fileShare == null) {
            throw new AppException(ResponseCodeEnums.CODE_600);
        }
        fileInfoService.getFile(response, fileId, fileShare.getUserId());
        return Result.ok();
    }


    /**
     * 保存文件到我的网盘(需要校验登录)
     * @param shareId  文件分享id
     * @param shareFileIds  需要保存的文件
     * @param myFolderId  保存到我的网盘的目录id
     * @return ok()
     */
    @PostMapping(value = "/saveShare")
    @GlobalInterceptor(checkParams = true)
    @Transactional(rollbackFor = Exception.class)
    public Result<?> saveShareFile(@VerifyParams(required = true) String shareId, @VerifyParams(required = true) String shareFileIds,
                                   @VerifyParams(required = true) String myFolderId, HttpServletRequest request) {
        // TODO: 2024/10/8 保存文件到我的网盘 添加定时任务删除回收站
        String token = request.getHeader("authorization");
        // 获取当前登录用户id
        String userId = (String) stringRedisTemplate.opsForHash().entries(RedisConstants.MYPAN_LOGIN_USER_KEY + token).get("userId");
        FileShare fileShare = fileShareService.getBaseMapper().selectById(shareId);
        if (fileShare == null || StringTools.isEmpty(userId)) {
            throw new AppException(ResponseCodeEnums.CODE_600);
        }
        // 校验需要保存的文件是不是分享的文件
        List<String> fileIds = Arrays.stream(shareFileIds.split(",")).collect(Collectors.toList());
        for (String fileId : fileIds) {
            if (!fileInfoService.checkFilePid(fileId, fileShare.getFileId(), fileShare.getUserId())) {
                throw new AppException(ResponseCodeEnums.CODE_600.getMsg());
            }
        }
        fileShareService.saveShareFile2MyPan(fileIds, myFolderId, userId, fileShare.getUserId());
        return Result.ok();
    }

    @PostMapping(value = "/createDownloadUrl/{shareId}/{fileId}")
    @GlobalInterceptor(checkParams = true, checkLoing = false)
    public Result<String> createDownloadUrl(@PathVariable(value = "shareId") @VerifyParams(required = true) String shareId,
                                            @PathVariable(value = "fileId") @VerifyParams(required = true) String fileId) {
        FileShare fileShare = fileShareService.getBaseMapper().selectById(shareId);
        if (fileShare == null) {
            throw new AppException(ResponseCodeEnums.CODE_600);
        }
        String downloadUrl = fileInfoService.createDownloadUrl(fileId, fileShare.getUserId());
        return Result.data(downloadUrl);
    }

    @GetMapping(value = "/download/{code}")
    @GlobalInterceptor(checkLoing = false, checkParams = true)
    public Result<?> downloadFile(HttpServletResponse response,
                                  @PathVariable(value = "code") @VerifyParams(required = true) String code) {
        fileInfoService.downloadFile(code, response);
        return Result.ok();
    }

    private WebShareInfoVO getWebShareInfoById(String shareId) {
        // 联合查询
        return fileShareService.getBaseMapper().selectJoinOne(WebShareInfoVO.class, new MPJLambdaWrapper<FileShare>()
                .selectAll(FileShare.class)
                .select(UserInfo::getNickName)
                .select(FileInfo::getFileName)
                .select(UserInfo::getAvatar)
                .leftJoin(FileInfo.class, FileInfo::getFileId, FileShare::getFileId)
                .leftJoin(UserInfo.class, UserInfo::getUserId, FileShare::getUserId)
                .eq(FileShare::getShareId, shareId).eq(FileInfo::getDelFlag, FileDelFlagEnums.NORMAL.getCode()));
    }

}

