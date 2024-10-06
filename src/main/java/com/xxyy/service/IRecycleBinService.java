package com.xxyy.service;

import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.baomidou.mybatisplus.extension.service.IService;
import com.xxyy.entity.FileInfo;
import com.xxyy.entity.vo.FileInfoVO;
import com.xxyy.entity.vo.PagingQueryVO;

/**
 * @author xy
 * @date 2024-10-02 20:45
 */
public interface IRecycleBinService extends IService<FileInfo> {

    PagingQueryVO<FileInfoVO> getRecycleList(Page<FileInfo> fileInfoPage, String token);

    void recoverFile(String token, String fileIds);

    void deleteFile(String token, String fileIds);
}
