package com.youmo.api;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.autoconfigure.domain.EntityScan;
import org.springframework.data.jpa.repository.config.EnableJpaRepositories;

@SpringBootApplication(scanBasePackages = "com.youmo")
@EntityScan(basePackages = "com.youmo.common.entity")
@EnableJpaRepositories(basePackages = "com.youmo.core.repository")
public class YouMoApiApplication {

    public static void main(String[] args) {
        SpringApplication.run(YouMoApiApplication.class, args);
    }
}
