package com.xxyy.controller;

import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.xxyy.annotation.GlobalInterceptor;
import com.xxyy.annotation.VerifyParams;
import com.xxyy.component.MailComponent;
import com.xxyy.entity.UserInfo;
import com.xxyy.entity.dto.FileQueryDTO;
import com.xxyy.entity.dto.SysSettingDTO;
import com.xxyy.entity.vo.FileInfoVO;
import com.xxyy.entity.vo.PagingQueryVO;
import com.xxyy.entity.vo.UserInfoVO;
import com.xxyy.service.IAdminService;
import com.xxyy.utils.StringTools;
import com.xxyy.utils.common.Result;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.*;

import javax.servlet.http.HttpServletResponse;

/**
 * @author xy
 * @date 2024-10-06 13:58
 */

@RestController
@RequestMapping(value = "/admin")
public class AdminController {

    @Autowired
    MailComponent mailComponent;

    @Autowired
    IAdminService adminService;

    @PostMapping(value = "/getSysSettings")
    @GlobalInterceptor(checkAdmin = true)
    public Result<SysSettingDTO> getSysSettings() {
        SysSettingDTO sysSetting = mailComponent.getSysSetting();
        return Result.data(sysSetting);
    }

    @PostMapping(value = "/saveSysSettings")
    @GlobalInterceptor(checkAdmin = true, checkParams = true)
    public Result<?> saveSysSettings(@ModelAttribute SysSettingDTO sysSettingDTO) {
        mailComponent.saveSysSetting(sysSettingDTO);
        return Result.ok();
    }

    @PostMapping(value = "/loadUserList")
    @GlobalInterceptor(checkAdmin = true)
    public Result<PagingQueryVO<UserInfoVO>> pageUserList(String pageNo, String pageSize, String nickNameFuzzy, String status) {
        Page<UserInfo> userInfoPage = new Page<>();
        userInfoPage.setSize(!StringTools.isEmpty(pageSize) ? Long.parseLong(pageSize): 15);
        userInfoPage.setCurrent(!StringTools.isEmpty(pageNo)? Long.parseLong(pageNo): 1);
        return Result.data(adminService.pageUserList(userInfoPage, nickNameFuzzy, status));
    }

    @PostMapping(value = "/updateUserStatus")
    @GlobalInterceptor(checkAdmin = true, checkParams = true)
    public Result<?> updateUserStatus(@VerifyParams(required = true) String userId, @VerifyParams(required = true) String status) {
        adminService.updateUserStatus(userId, status);
        return Result.ok();
    }

    @PostMapping(value = "/updateUserSpace")
    @GlobalInterceptor(checkAdmin = true, checkParams = true)
    public Result<?> updateUserSpace(@VerifyParams(required = true) String userId, @VerifyParams(required = true) String changeSpace) {
        adminService.updateUserSpace(userId, changeSpace);
        return Result.ok();
    }

    @PostMapping(value = "/loadFileList")
    @GlobalInterceptor(checkAdmin = true)
    public Result<PagingQueryVO<FileInfoVO>> pageFileList(FileQueryDTO fileQueryDTO) {
        Page<FileInfoVO> fileInfoPage = new Page<>();
        fileInfoPage.setSize(!StringTools.isEmpty(fileQueryDTO.getPageSize()) ? Long.parseLong(fileQueryDTO.getPageSize()): 15);
        fileInfoPage.setCurrent(!StringTools.isEmpty(fileQueryDTO.getPageNo())? Long.parseLong(fileQueryDTO.getPageNo()): 1);
        return Result.data(adminService.pageFileList(fileInfoPage, fileQueryDTO));
    }

    @PostMapping(value = "/createDownloadUrl/{userId}/{fileId}")
    @GlobalInterceptor(checkAdmin = true, checkParams = true)
    public Result<String> createDownloadUrl(@VerifyParams(required = true) @PathVariable(value = "userId") String userId,
                                            @VerifyParams(required = true) @PathVariable(value = "fileId") String fileId) {
        return Result.data(adminService.createDownloadUrl(userId, fileId));
    }

    @GetMapping(value = "/download/{code}")
    @GlobalInterceptor(checkParams = true, checkLoing = false)
    public void downloadFile(@PathVariable(name = "code") @VerifyParams(required = true) String code, HttpServletResponse response) {
        adminService.downloadFile(code, response);
    }

    @PostMapping(value = "/delFile")
    @GlobalInterceptor(checkAdmin = true, checkParams = true)
    public Result<?> deleteFile(@VerifyParams(required = true) String fileIdAndUserIds) {
        adminService.deleteFile(fileIdAndUserIds);
        return Result.ok();
    }

    @GetMapping(value = "/ts/getVideoInfo/{userId}/{fileId}")
    @GlobalInterceptor(checkAdmin = true, checkParams = true)
    public Result<?> getVideoInfo(HttpServletResponse response,
                                  @PathVariable(value = "userId") @VerifyParams(required = true) String userId,
                                  @PathVariable(value = "fileId") @VerifyParams(required = true) String fileId) {
        adminService.getVideoInfo(userId, fileId, response);
        return Result.ok();
    }


}
