package com.xxyy.service.impl;

import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.github.yulichang.wrapper.MPJLambdaWrapper;
import com.xxyy.entity.FileInfo;
import com.xxyy.entity.FileShare;
import com.xxyy.entity.enums.FileDelFlagEnums;
import com.xxyy.entity.enums.ResponseCodeEnums;
import com.xxyy.entity.enums.ShareValidTypeEnums;
import com.xxyy.entity.vo.FileShareVO;
import com.xxyy.entity.vo.PagingQueryVO;
import com.xxyy.mapper.FileShareMapper;
import com.xxyy.service.IFileInfoService;
import com.xxyy.service.IFileShareService;
import com.xxyy.utils.CodeConstants;
import com.xxyy.utils.RedisConstants;
import com.xxyy.utils.StringTools;
import com.xxyy.utils.common.AppException;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Arrays;
import java.util.Calendar;
import java.util.Date;
import java.util.List;
import java.util.stream.Collectors;


/**
 * @author xy
 * @date 2024-10-05 19:07
 */

@Service
public class FileShareServiceImpl extends ServiceImpl<FileShareMapper, FileShare> implements IFileShareService {

    @Autowired
    StringRedisTemplate stringRedisTemplate;

    @Autowired
    IFileInfoService fileInfoService;

    @Override
    public PagingQueryVO<FileShareVO> pageShareList(String token, Page<FileShareVO> fileSharePage) {
        String userId = (String)stringRedisTemplate.opsForHash().entries(RedisConstants.MYPAN_LOGIN_USER_KEY + token).get("userId");
        // mybatis-plus联合查询 查询File表中的字段加入到FileShare类中
        Page<FileShareVO> fileSharePageVO = getBaseMapper().selectJoinPage(fileSharePage, FileShareVO.class,
                new MPJLambdaWrapper<FileShare>().selectAll(FileShare.class)
                        .select(FileInfo::getFileName).select(FileInfo::getFolderType)
                        .select(FileInfo::getFileCategory).select(FileInfo::getFileType)
                        .select(FileInfo::getFileCover).leftJoin(FileInfo.class, FileInfo::getFileId, FileShare::getFileId)
                        .eq(FileInfo::getDelFlag, FileDelFlagEnums.NORMAL.getCode())
                        .eq(FileInfo::getUserId, userId));
        return PagingQueryVO.ofShare(fileSharePageVO);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public FileShareVO shareFile(String token, String fileId, Integer validType, String code) {
        String userId = (String)stringRedisTemplate.opsForHash().entries(RedisConstants.MYPAN_LOGIN_USER_KEY + token).get("userId");
        ShareValidTypeEnums shareValidType = ShareValidTypeEnums.getShareValidType(validType);
        if (shareValidType == null) {
            throw new AppException(ResponseCodeEnums.CODE_600);
        }
        Date curDate = new Date();
        FileShare fileShare = new FileShare();
        fileShare.setShareTime(curDate);
        fileShare.setType(shareValidType.getType());
        fileShare.setUserId(userId);
        fileShare.setFileId(fileId);
        Calendar calendar = Calendar.getInstance();
        calendar.add(Calendar.DAY_OF_YEAR, shareValidType.getExpire());
        fileShare.setExpireTime(calendar.getTime());
        fileShare.setShareId(StringTools.getRandomString(CodeConstants.LENGTH_20));
        fileShare.setCode(StringTools.isEmpty(code)? StringTools.getRandomString(CodeConstants.LENGTH_5) : code);
        save(fileShare);
        return FileShareVO.of(fileShare, null);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void cancelShare(String token, String shareIds) {
        String userId = (String)stringRedisTemplate.opsForHash().entries(RedisConstants.MYPAN_LOGIN_USER_KEY + token).get("userId");
        if (StringTools.isEmpty(shareIds)) {
            throw new AppException(ResponseCodeEnums.CODE_600);
        }
        List<String> ids = Arrays.stream(shareIds.split(",")).collect(Collectors.toList());
        removeByIds(ids);
    }
}
