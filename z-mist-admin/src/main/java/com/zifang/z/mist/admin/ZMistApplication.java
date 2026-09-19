package com.zifang.z.mist.admin;

import org.mybatis.spring.annotation.MapperScan;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.context.annotation.ComponentScan;

/**
 * z-mist 管理端启动类
 */
@SpringBootApplication
@ComponentScan(basePackages = {"com.zifang.z.mist"})
@MapperScan("com.zifang.z.mist.core.domain.mapper")
public class ZMistApplication {

    public static void main(String[] args) {
        SpringApplication.run(ZMistApplication.class, args);
    }
}