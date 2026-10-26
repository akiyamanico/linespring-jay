package com.linepyjay;

import com.linepyjay.service.LineClientService;
import com.linepyjay.service.PollService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.CommandLineRunner;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.context.annotation.Bean;
import org.springframework.scheduling.annotation.EnableScheduling;

@SpringBootApplication
@EnableScheduling
public class LinepyJayApplication {

    private static final Logger log = LoggerFactory.getLogger(LinepyJayApplication.class);

    public static void main(String[] args) {
        SpringApplication.run(LinepyJayApplication.class, args);
    }

    @Bean
    public CommandLineRunner startBot(LineClientService lineClientService, PollService pollService) {
        return args -> {
            log.info("Attempting to log in to LINE...");
            lineClientService.login();
            log.info("Login successful! Auth Token: {}", lineClientService.getAuthToken());
            log.info("Secure bot is running with E2EE and Letter Sealing...");
            pollService.startPolling();
        };
    }
}
