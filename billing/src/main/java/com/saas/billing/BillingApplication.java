package com.saas.billing;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.scheduling.annotation.EnableScheduling;
import com.saas.billing.common.config.JwtProperties;

@SpringBootApplication
@EnableConfigurationProperties(JwtProperties.class)
@EnableScheduling
public class BillingApplication {

	public static void main(String[] args) {
		SpringApplication.run(BillingApplication.class, args);
	}
}