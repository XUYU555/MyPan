package com.xxyy.service;

import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.baomidou.mybatisplus.extension.service.IService;
import com.xxyy.entity.FileShare;
import com.xxyy.entity.vo.FileShareVO;
import com.xxyy.entity.vo.PagingQueryVO;

/**
 * @author xy
 * @date 2024-10-05 19:07
 */
public interface IFileShareService extends IService<FileShare> {

    PagingQueryVO<FileShareVO> pageShareList(String token, Page<FileShare> fileSharePage);

    FileShareVO shareFile(String token, String fileId, Integer validType, String code);

    void cancelShare(String token, String shareIds);
}
