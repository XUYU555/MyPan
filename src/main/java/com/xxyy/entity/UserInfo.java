package com.xxyy.entity;

import com.baomidou.mybatisplus.annotation.IdType;
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

    private String nickName;

    private String password;

    private String email;

    private String qqOpenId;

    private String avatar;

    private Date registerTime;

    private Date lastLoginTime;

    private Long useSpace;

    private Long totalSpace;

    private int status;

}
