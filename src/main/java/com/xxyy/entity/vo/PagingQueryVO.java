package com.xxyy.entity.vo;

import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.xxyy.entity.FileInfo;
import com.xxyy.entity.FileShare;
import lombok.Data;

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

    public static PagingQueryVO<FileShareVO> ofPage(Page<FileShare> page) {
        PagingQueryVO<FileShareVO> pagingQueryVO = new PagingQueryVO<>();
        pagingQueryVO.setPageSize(page.getSize());
        pagingQueryVO.setPageNo(page.getCurrent());
        pagingQueryVO.setPageTotal(page.getPages());
        pagingQueryVO.setTotalCount(page.getTotal());
        return pagingQueryVO;
    }

}
