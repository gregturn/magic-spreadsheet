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

import lombok.extern.slf4j.Slf4j;

import java.time.LocalDate;
import java.time.format.DateTimeParseException;
import java.util.Arrays;
import java.util.Comparator;
import java.util.Optional;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;
import org.springframework.data.domain.Sort;
import org.springframework.http.codec.multipart.FilePart;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RequestPart;

/**
 * @author Greg Turnquist
 */
@Controller
@Slf4j
class AmsController {

	private final AmsDataRepository amsDataRepository;
	private final LoaderService loaderService;

	AmsController(AmsDataRepository amsDataRepository, LoaderService loaderService) {
		
		this.amsDataRepository = amsDataRepository;
		this.loaderService = loaderService;
	}

	@GetMapping("/rawAmsData")
	Mono<String> rawAmsData(@RequestParam(name = "window", required = false) Optional<String> optionalWindow, Model model) {

		String window = optionalWindow.orElse("all");

		model.addAttribute("filterOptions", Arrays.asList(
			new FilterOption("all", "Lifetime", window.equals("all")),
			new FilterOption("90days", "Last 90 days", window.equals("90days")),
			new FilterOption("30days", "Last 30 days", window.equals("30days")),
			new FilterOption("15days", "Last 15 days", window.equals("15days"))
		));

		Sort sortByDateAndCampaignName = Sort.by("date", "campaignName");

		if ("all".equals(window)) {

			model.addAttribute("amsData", amsDataRepository
				.findAll(sortByDateAndCampaignName)
				.map(AmsDataDTO::new));

		} else if ("90days".equals(window)) {

			model.addAttribute("amsData", amsDataRepository
				.findByDateAfter(LocalDate.now().minusDays(90), sortByDateAndCampaignName)
				.map(AmsDataDTO::new));

		} else if ("30days".equals(window)) {

			model.addAttribute("amsData", amsDataRepository
				.findByDateAfter(LocalDate.now().minusDays(30), sortByDateAndCampaignName)
				.map(AmsDataDTO::new));

		} else if ("15days".equals(window)) {

			model.addAttribute("amsData", amsDataRepository
				.findByDateAfter(LocalDate.now().minusDays(15), sortByDateAndCampaignName)
				.map(AmsDataDTO::new));
		}

		return Mono.just("rawAmsData");
	}

	@PostMapping("/import-ams")
	Mono<String> importAmsReport(@RequestPart(name = "csvFile") Flux<FilePart> amsReport,
								 @RequestPart(name = "date") String date) {

		return amsReport
			.sort(Comparator.comparing(FilePart::filename))
			.flatMap(csvFilePart -> {
				if (date.equals("")) {
					return loaderService.importAmsReport(csvFilePart,
						optionalDateInFilename(csvFilePart.filename()).orElse(LocalDate.now()));
				} else {
					try {
						return loaderService.importAmsReport(csvFilePart, LocalDate.parse(date));
					} catch (DateTimeParseException e) {
						throw new RuntimeException(e);
					}
				}
			})
			.log("import-done")
			.then(Mono.just("redirect:/"));
	}

	@DeleteMapping("/deleteAllAmsData")
	Mono<String> deleteAllAmsData() {

		return amsDataRepository.deleteAll()
			.thenReturn("redirect:/rawAmsData");
	}

	private Optional<LocalDate> optionalDateInFilename(String filename) {


		Pattern datePattern = Pattern.compile("\\d+-\\d+-\\d+");
		Matcher matcher = datePattern.matcher(filename);

		while (matcher.find()) {
			try {
				return Optional.of(LocalDate.parse(matcher.group()));
			} catch (DateTimeParseException e) {
				return Optional.empty();
			}
		}

		return Optional.empty();
	}
}
