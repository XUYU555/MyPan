package com.xxyy.entity.vo;

import com.fasterxml.jackson.annotation.JsonFormat;
import lombok.Data;

import java.util.Date;

/**
 * @author xy
 * @date 2024-10-08 14:09
 */

@Data
public class WebShareInfoVO {

    private String nickName;

    private String fileName;

    private String fileId;

    private String userId;

    private String avatar;

    @JsonFormat(pattern = "yyyy:MM:dd hh:mm:ss")
    private Date shareTime;

    @JsonFormat(pattern = "yyyy:MM:dd hh:mm:ss")
    private Date expireTime;

    private boolean currentUser = false;

}
