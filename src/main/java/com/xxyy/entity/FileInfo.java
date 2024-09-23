package com.xxyy.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.experimental.Accessors;

import java.io.Serializable;
import java.util.Date;

/**
 * @author xy
 * @date 2024-09-22 10:52
 */

@Data
@Accessors(chain = true)
@EqualsAndHashCode(callSuper = false)
@TableName(value = "file_info")
public class FileInfo implements Serializable {

    private static final long serialVersionUID = 1L;

    // 文件id
    @TableId(value = "file_id", type = IdType.NONE)
    private String fileId;

    // 用户id
    @TableField(value = "user_id")
    private String userId;

    // 文件名
    @TableField(value = "file_name")
    private String fileName;

    // 文件MD5值
    @TableField(value = "file_md5")
    private String fileMd5;

    // 父级id
    @TableField(value = "file_pid")
    private String filePid;

    // 文件大小
    @TableField(value = "file_size")
    private Long fileSize;

    // 文件封面
    @TableField(value = "file_cover")
    private String fileCover;

    // 文件路径
    @TableField(value = "file_path")
    private String filePath;

    // 创建时间
    @TableField(value = "create_time")
    private Date createTime;

    // 最后修改时间
    @TableField(value = "last_update_time")
    private Date lastUpdateTime;

    // 0:文件 1:目录
    @TableField(value = "folder_type")
    private Integer folderType;

    // 文件分类 1:视频 2:音频 3:图片 4:文档 5:其他
    @TableField(value = "file_category")
    private Integer fileCategory;

    // 1:视频 2:音频 3:图片 4:pdf 5:doc 6:excel 7:text 8:code 9:zip 10:其他
    @TableField(value = "file_type")
    private Integer fileType;

    // 0:转码中 1:转码失败 2:转码成功
    @TableField(value = "status")
    private Integer status;

    // 进入回收站时间
    @TableField(value = "recovery_time")
    private Date recoveryTime;

    // 标记删除 1:回收站 2：正常
    @TableField(value = "del_flag")
    private Integer delFlag;
}
