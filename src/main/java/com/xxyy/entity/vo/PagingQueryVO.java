package com.xxyy.entity.vo;

import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.xxyy.entity.FileInfo;
import com.xxyy.entity.FileShare;
import com.xxyy.entity.UserInfo;
import lombok.Data;
import org.apache.catalina.User;

import java.util.List;
import java.util.stream.Collectors;

/**
 * @author xy
 * @date 2024-09-22 11:19
 */

@Data
public class PagingQueryVO <T> {

    private long totalCount;

    private long pageSize;

    private long pageNo;

    private long pageTotal;

    private List<T> list;

    public static PagingQueryVO<FileInfoVO> of(Page<FileInfo> page) {
        PagingQueryVO<FileInfoVO> pagingQueryVO = new PagingQueryVO<>();
        pagingQueryVO.setPageSize(page.getSize());
        pagingQueryVO.setPageNo(page.getCurrent());
        pagingQueryVO.setPageTotal(page.getPages());
        pagingQueryVO.setTotalCount(page.getTotal());
        List<FileInfoVO> collect = page.getRecords().stream().map(FileInfoVO::of).collect(Collectors.toList());
        pagingQueryVO.setList(collect);
        return pagingQueryVO;
    }

    public static PagingQueryVO<FileShareVO> ofShare(Page<FileShareVO> page) {
        PagingQueryVO<FileShareVO> pagingQueryVO = new PagingQueryVO<>();
        pagingQueryVO.setPageSize(page.getSize());
        pagingQueryVO.setPageNo(page.getCurrent());
        pagingQueryVO.setPageTotal(page.getPages());
        pagingQueryVO.setTotalCount(page.getTotal());
        pagingQueryVO.setList(page.getRecords());
        return pagingQueryVO;
    }

    public static PagingQueryVO<FileInfoVO> ofFile(Page<FileInfoVO> page) {
        PagingQueryVO<FileInfoVO> pagingQueryVO = new PagingQueryVO<>();
        pagingQueryVO.setPageSize(page.getSize());
        pagingQueryVO.setPageNo(page.getCurrent());
        pagingQueryVO.setPageTotal(page.getPages());
        pagingQueryVO.setTotalCount(page.getTotal());
        pagingQueryVO.setList(page.getRecords());
        return pagingQueryVO;
    }

    public static PagingQueryVO<UserInfoVO> ofUser(Page<UserInfo> page) {
        PagingQueryVO<UserInfoVO> pagingQueryVO = new PagingQueryVO<>();
        pagingQueryVO.setPageSize(page.getSize());
        pagingQueryVO.setPageNo(page.getCurrent());
        pagingQueryVO.setPageTotal(page.getPages());
        pagingQueryVO.setTotalCount(page.getTotal());
        List<UserInfoVO> collect = page.getRecords().stream().map(UserInfoVO::of).collect(Collectors.toList());
        pagingQueryVO.setList(collect);
        return pagingQueryVO;
    }

}
