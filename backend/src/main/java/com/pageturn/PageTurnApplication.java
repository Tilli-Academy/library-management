package com.pageturn;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.ConfigurationPropertiesScan;

@SpringBootApplication
@ConfigurationPropertiesScan
public class PageTurnApplication {

    public static void main(String[] args) {
        SpringApplication.run(PageTurnApplication.class, args);
    }
}
