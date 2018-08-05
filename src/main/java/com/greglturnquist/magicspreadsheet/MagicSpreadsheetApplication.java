package com.greglturnquist.magicspreadsheet;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.autoconfigure.mongo.MongoClientSettingsBuilderCustomizer;
import org.springframework.context.annotation.Bean;

import com.mongodb.connection.ConnectionPoolSettings;

@SpringBootApplication
public class MagicSpreadsheetApplication {

	public static void main(String[] args) {
		SpringApplication.run(MagicSpreadsheetApplication.class, args);
	}

	@Bean
	MongoClientSettingsBuilderCustomizer customizer() {
		return clientSettingsBuilder -> clientSettingsBuilder.connectionPoolSettings(ConnectionPoolSettings.builder()
			.maxWaitQueueSize(15000)
			.build());
	}
}
