package com.incidentops;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.scheduling.annotation.EnableAsync;

@SpringBootApplication
@EnableAsync
public class IncidentOpsApplication {

    public static void main(String[] args) {
        SpringApplication.run(IncidentOpsApplication.class, args);
    }
}
