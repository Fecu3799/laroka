package com.laroka.backend;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

// @EnableScheduling vive en SchedulingConfig (@Profile("!test")) para que los jobs
// no disparen durante los tests de integración y deadlockeen con el TRUNCATE.
@SpringBootApplication
public class BackendApplication {

	public static void main(String[] args) {
		SpringApplication.run(BackendApplication.class, args);
	}

}
