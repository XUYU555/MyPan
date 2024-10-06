package com.xxyy.service;

import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.xxyy.entity.FileInfo;
import com.xxyy.entity.UserInfo;
import com.xxyy.entity.dto.FileQueryDTO;
import com.xxyy.entity.vo.FileInfoVO;
import com.xxyy.entity.vo.PagingQueryVO;
import com.xxyy.entity.vo.UserInfoVO;

import javax.servlet.http.HttpServletResponse;

/**
 * @author xy
 * @date 2024-10-06 14:21
 */
public interface IAdminService {

    PagingQueryVO<UserInfoVO> pageUserList(Page<UserInfo> userInfoPage, String nickNameFuzzy, String status);

    void updateUserStatus(String userId, String status);

    void updateUserSpace(String userId, String changeSpace);

    PagingQueryVO<FileInfoVO> pageFileList(Page<FileInfoVO> fileInfoPage, FileQueryDTO fileQueryDTO);

    String createDownloadUrl(String userId, String fileId);

    void downloadFile(String code, HttpServletResponse response);

    void deleteFile(String fileIdAndUserIds);

    void getVideoInfo(String userId, String fileId, HttpServletResponse response);
}
