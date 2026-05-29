package com.shop;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.ConfigurationPropertiesScan;
import org.springframework.context.ConfigurableApplicationContext;
import org.springframework.core.env.Environment;

import java.util.Arrays;

/**
 * Entry point of the mini-shop backend.
 *
 * <p>No business logic lives here — this is the Spring Boot bootstrap class.
 * Layered architecture (controller -> service -> domain -> repository) is built
 * out in subsequent phases.</p>
 */
@SpringBootApplication
@ConfigurationPropertiesScan
public class ShopApplication {

    private static final Logger log = LoggerFactory.getLogger(ShopApplication.class);

    public static void main(String[] args) {
        ConfigurableApplicationContext context = SpringApplication.run(ShopApplication.class, args);
        Environment env = context.getEnvironment();
        log.info("mini-shop backend started. Active profiles: {}",
                Arrays.toString(env.getActiveProfiles()));
    }
}
