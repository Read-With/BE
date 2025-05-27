package com.kw.readwith;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.data.jpa.repository.config.EnableJpaAuditing;

@SpringBootApplication
@EnableJpaAuditing
public class ReadwithApplication {

	public static void main(String[] args) {
		SpringApplication.run(ReadwithApplication.class, args);
	}

}
