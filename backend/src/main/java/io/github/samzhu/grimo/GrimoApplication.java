package io.github.samzhu.grimo;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

@SpringBootApplication
public class GrimoApplication {

	public static void main(String[] args) {
		grimoApplication().run(args);
	}

	static SpringApplication grimoApplication() {
		SpringApplication application = new SpringApplication(GrimoApplication.class);
		application.setHeadless(false);
		return application;
	}

}
