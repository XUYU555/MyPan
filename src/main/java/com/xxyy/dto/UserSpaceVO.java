package com.xxyy.dto;

import com.alibaba.fastjson.JSON;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * @author xy
 * @date 2024-09-20 13:32
 */

@Data
@AllArgsConstructor
@NoArgsConstructor
public class UserSpaceVO {

    private Long useSpace;

    private Long totalSpace;

    public String toJSONString() {
        return JSON.toJSONString(this);
    }
}
