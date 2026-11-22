package com.timelapse.backend;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.scheduling.annotation.EnableScheduling;

@SpringBootApplication
@EnableScheduling
public class BackendApplication {

	public static void main(String[] args) throws Exception {
		if (args.length > 0 && "--refactoring-worker".equals(args[0])) {
			com.timelapse.backend.service.refactoring.RefactoringWorker.run(args);
			return;
		}
		SpringApplication.run(BackendApplication.class, args);
	}

}
