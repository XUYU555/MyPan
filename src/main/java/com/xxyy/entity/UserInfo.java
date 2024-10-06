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
 * @date 2024-09-17 17:08
 */

@Data
@Accessors(chain = true)
@EqualsAndHashCode(callSuper = false)
@TableName(value = "user_info")
public class UserInfo implements Serializable {

    private static final long serialVersionUID = 1L;

    @TableId(value = "user_id", type = IdType.NONE)
    private String userId;

    @TableField(value = "nick_name")
    private String nickName;

    @TableField(value = "password")
    private String password;

    @TableField(value = "email")
    private String email;

    @TableField(value = "qq_open_id")
    private String qqOpenId;

    @TableField(value = "avatar")
    private String avatar;

    @TableField(value = "register_time")
    private Date registerTime;

    @TableField(value = "last_login_time")
    private Date lastLoginTime;

    @TableField(value = "use_space")
    private Long useSpace;

    @TableField(value = "total_space")
    private Long totalSpace;

    @TableField(value = "status")
    private int status;

}
