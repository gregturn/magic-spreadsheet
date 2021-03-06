package com.greglturnquist.magicspreadsheet;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.autoconfigure.mongo.MongoClientSettingsBuilderCustomizer;
import org.springframework.cache.annotation.EnableCaching;
import org.springframework.context.annotation.Bean;
import org.springframework.web.filter.reactive.HiddenHttpMethodFilter;

import com.mongodb.connection.ConnectionPoolSettings;

@SpringBootApplication
@EnableCaching
public class MagicSpreadsheetApplication {

	public static void main(String[] args) {
		SpringApplication.run(MagicSpreadsheetApplication.class, args);
	}

	@Bean
	MongoClientSettingsBuilderCustomizer customizer() {
		return clientSettingsBuilder -> clientSettingsBuilder.connectionPoolSettings(ConnectionPoolSettings.builder()
			.maxWaitQueueSize(30000)
			.build());
	}

	@Bean
	HiddenHttpMethodFilter hiddenHttpMethodFilter() {
		return new HiddenHttpMethodFilter();
	}
}
