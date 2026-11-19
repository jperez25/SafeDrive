package com.qnt.safedrive.tripprocessor;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.scheduling.annotation.EnableScheduling;

@SpringBootApplication
@EnableScheduling
public class TripProcessorApplication {

    public static void main(String[] args) {
        SpringApplication.run(TripProcessorApplication.class, args);
    }
}
