package com.evilink.crypto_link;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.scheduling.annotation.EnableScheduling;

@SpringBootApplication
@EnableScheduling
public class CryptoLinkApplication {

	public static void main(String[] args) {
		SpringApplication.run(CryptoLinkApplication.class, args);
	}

}
