package org.example;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

@SpringBootApplication
public class UserServiceApplication {


	private final MyTelegramBot bot;


	public UserServiceApplication(MyTelegramBot bot) {
		this.bot = bot;
	}

	public static void main(String[] args) {
		SpringApplication.run(UserServiceApplication.class, args);
	}


}
