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
import com.xxyy.entity.vo.FileInfoVO;
import com.xxyy.entity.vo.PagingQueryVO;
import com.xxyy.entity.vo.UserInfoVO;
import com.xxyy.entity.vo.WebShareInfoVO;
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
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpSession;
import java.util.Date;

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

