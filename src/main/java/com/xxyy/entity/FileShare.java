package com.xxyy.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.experimental.Accessors;

import java.util.Date;

/**
 * @author xy
 * @date 2024-10-05 18:51
 */

@Data
@Accessors(chain = true)
@EqualsAndHashCode(callSuper = false)
@TableName(value = "file_share")
public class FileShare {

    @TableId(value = "share_id", type = IdType.NONE)
    private String shareId;

    @TableField(value = "file_id")
    private String fileId;

    @TableField(value = "user_id")
    private String userId;

    @TableField(value = "share_time")
    private Date shareTime;

    @TableField(value = "expire_time")
    private Date expireTime;

    @TableField(value = "valid_type")
    private Integer type;

    @TableField(value = "code")
    private String code;

    @TableField(value = "show_count")
    private Integer showCount;

}
