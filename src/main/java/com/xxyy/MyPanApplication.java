package com.xxyy;

import org.mybatis.spring.annotation.MapperScan;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

/**
 * @author xy
 * @date 2024-09-17 11:08
 */


@SpringBootApplication(scanBasePackages = "com.xxyy")
@MapperScan(basePackages = "com.xxyy.mapper")
public class MyPanApplication {

    public static void main(String[] args) {
        SpringApplication.run(MyPanApplication.class, args);
    }

}
