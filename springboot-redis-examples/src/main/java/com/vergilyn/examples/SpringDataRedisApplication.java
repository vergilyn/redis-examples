package com.vergilyn.examples;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;


@SpringBootApplication
public class SpringDataRedisApplication {

    public static void main(String[] args) {
        SpringApplication application = new SpringApplication(SpringDataRedisApplication.class);
        application.setAdditionalProfiles("datasource");
        application.run(args);
    }
}
