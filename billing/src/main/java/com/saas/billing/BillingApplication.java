package com.saas.billing;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.event.ApplicationEnvironmentPreparedEvent;
import org.springframework.context.ApplicationListener;
import org.springframework.core.env.Environment;

@SpringBootApplication
public class BillingApplication {

	public static void main(String[] args) {
		SpringApplication app = new SpringApplication(BillingApplication.class);
		app.addListeners((ApplicationListener<ApplicationEnvironmentPreparedEvent>) event -> {
			Environment env = event.getEnvironment();
			System.out.println("=== DB DEBUG ===");
			System.out.println("URL: " + env.getProperty("spring.datasource.url"));
			System.out.println("USER: " + env.getProperty("spring.datasource.username"));
			System.out.println("PASS: " + env.getProperty("spring.datasource.password"));
			System.out.println("================");
		});
		app.run(args);
	}
}