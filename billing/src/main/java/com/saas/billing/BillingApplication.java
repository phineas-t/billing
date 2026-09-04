package com.saas.billing;

import jakarta.annotation.PostConstruct;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.scheduling.annotation.EnableAsync;
import org.springframework.scheduling.annotation.EnableScheduling;
import com.saas.billing.common.config.JwtProperties;

import java.util.TimeZone;

@SpringBootApplication
@EnableConfigurationProperties(JwtProperties.class)
@EnableScheduling
@EnableAsync
public class BillingApplication {

	public static void main(String[] args) {
		TimeZone.setDefault(TimeZone.getTimeZone("UTC"));
		SpringApplication.run(BillingApplication.class, args);
	}
}