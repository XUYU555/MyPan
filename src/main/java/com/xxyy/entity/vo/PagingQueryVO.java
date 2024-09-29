package com.xxyy.entity.vo;

import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.xxyy.entity.FileInfo;
import lombok.Data;

import java.util.List;
import java.util.stream.Collectors;

/**
 * @author xy
 * @date 2024-09-22 11:19
 */

@Data
public class PagingQueryVO {

    private long totalCount;

    private long pageSize;

    private long pageNo;

    private long pageTotal;

    private List<FileInfoVO> list;

    public static PagingQueryVO of(Page<FileInfo> page) {
        PagingQueryVO pagingQueryVO = new PagingQueryVO();
        pagingQueryVO.setPageSize(page.getSize());
        pagingQueryVO.setPageNo(page.getCurrent());
        pagingQueryVO.setPageTotal(page.getPages());
        pagingQueryVO.setTotalCount(page.getTotal());
        List<FileInfoVO> collect = page.getRecords().stream().map(FileInfoVO::of).collect(Collectors.toList());
        pagingQueryVO.setList(collect);
        return pagingQueryVO;
    }

}
