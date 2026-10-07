package com.daysheet;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.scheduling.annotation.EnableScheduling;

@SpringBootApplication
@EnableScheduling
public class DaysheetApplication {
    public static void main(String[] args) {
        SpringApplication.run(DaysheetApplication.class, args);
    }
}
