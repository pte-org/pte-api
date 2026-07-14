package com.aptis.aptis_api;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.persistence.autoconfigure.EntityScan;
import org.springframework.data.jpa.repository.config.EnableJpaRepositories;

@SpringBootApplication(scanBasePackages = "com.aptis")
@EntityScan(basePackages = "com.aptis.modules")
@EnableJpaRepositories(basePackages = "com.aptis.modules")
public class AptisApiApplication {

	public static void main(String[] args) {
		SpringApplication.run(AptisApiApplication.class, args);
	}

}
