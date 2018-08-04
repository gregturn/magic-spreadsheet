/*
 * Copyright 2018 the original author or authors.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.greglturnquist.magicspreadsheet;

import java.time.LocalDate;

import org.springframework.boot.CommandLineRunner;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.io.ClassPathResource;

/**
 * @author Greg Turnquist
 */
@Configuration
class SpreadsheetLoader {

//	@Bean
	CommandLineRunner loadMagicSpreadsheet(LoaderService loaderService) {
		return args -> {
			ClassPathResource magicSpreadsheet = new ClassPathResource("sample-magic-spreadsheet.xlsm");
			loaderService.loadMagicSpreadsheet(magicSpreadsheet.getInputStream());
		};
	}

//	@Bean
	CommandLineRunner importAmsReport(LoaderService loaderService) {
		return args -> {
			ClassPathResource amsReport = new ClassPathResource("sample-ams-report.csv");
			loaderService.loadAmsReport(LoaderService.toReader(amsReport.getFile().getAbsolutePath()), LocalDate.now())
				.subscribe();
		};
	}

}
