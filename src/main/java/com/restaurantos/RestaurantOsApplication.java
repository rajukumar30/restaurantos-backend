package com.restaurantos;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.scheduling.annotation.EnableScheduling;

@SpringBootApplication
@EnableScheduling
public class RestaurantOsApplication {

    public static void main(String[] args) {
        SpringApplication.run(RestaurantOsApplication.class, args);
    }
}
