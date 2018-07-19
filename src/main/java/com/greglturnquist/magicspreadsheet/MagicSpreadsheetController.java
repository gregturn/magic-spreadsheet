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

import java.io.File;
import java.io.IOException;
import java.nio.file.Paths;
import java.time.Duration;
import java.time.Instant;
import java.util.Arrays;
import java.util.Comparator;
import java.util.Date;
import java.util.Optional;

import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;
import org.springframework.core.io.FileSystemResource;
import org.springframework.http.codec.multipart.FilePart;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RequestPart;
import org.springframework.web.bind.annotation.ResponseBody;

/**
 * @author Greg Turnquist
 */
@Controller
@Slf4j
public class MagicSpreadsheetController {

	private static String UPLOAD_ROOT = "upload-dir";

	private final AmsDataRepository amsDataRepository;
	private final AdTableRepository adTableRepository;
	private final AdService adService;
	private final LoaderService loaderService;

	public MagicSpreadsheetController(AmsDataRepository amsDataRepository,
									  AdTableRepository adTableRepository,
									  AdService adService, LoaderService loaderService) {

		this.amsDataRepository = amsDataRepository;
		this.adTableRepository = adTableRepository;
		this.adService = adService;
		this.loaderService = loaderService;
	}

	@GetMapping("/")
	Mono<String> index(Model model) {
		return Mono.just("index");
	}

	@GetMapping("/ads")
	Mono<String> ads(Model model) {

		model.addAttribute("adTable", adTableRepository.findAll().map(AdTableDTO::new));

		return Mono.just("ads");
	}

	@GetMapping("/rawdata")
	Mono<String> raw(@RequestParam(name = "window", required = false) Optional<String> optionalWindow, Model model) {

		String window = optionalWindow.orElse("all");
		
		model.addAttribute("filterOptions", Arrays.asList(
			new FilterOption("all", "Lifetime", window.equals("all")),
			new FilterOption("30days", "Last 30 days", window.equals("30days")),
			new FilterOption("15days", "Last 15 days", window.equals("15days"))
		));

		if ("all".equals(window)) {

			model.addAttribute("amsData", amsDataRepository
				.findAll()
				.map(AmsDataDTO::new));
		} else if ("15days".equals(window)) {

			model.addAttribute("amsData", amsDataRepository
				.findByDateAfter(Date.from(Instant.now().minus(Duration.ofDays(15))))
				.map(AmsDataDTO::new));
		} else if ("30days".equals(window)) {
			
			model.addAttribute("amsData", amsDataRepository
				.findByDateAfter(Date.from(Instant.now().minus(Duration.ofDays(30))))
				.map(AmsDataDTO::new));
		}

		return Mono.just("raw");
	}

	@GetMapping("/conversions")
	Mono<String> conversions(@RequestParam(name = "window", required = false) Optional<String> optionalWindow, Model model) {

		String window = optionalWindow.orElse("all");

		model.addAttribute("filterOptions", Arrays.asList(
			new FilterOption("all", "Lifetime", window.equals("all")),
			new FilterOption("30days", "Last 30 days", window.equals("30days")),
			new FilterOption("15days", "Last 15 days", window.equals("15days"))
		));

		if ("all".equals(window)) {

			model.addAttribute("conversionData",
				adService.clicksToConvert(Optional.empty())
					.sort(Comparator.comparing(BookDTO::getTitle)));
		} else if ("15days".equals(window)) {

			model.addAttribute("conversionData",
				adService.clicksToConvert(Optional.of(Date.from(Instant.now().minus(Duration.ofDays(15)))))
					.sort(Comparator.comparing(BookDTO::getTitle)));
		} else if ("30days".equals(window)) {

			model.addAttribute("conversionData",
				adService.clicksToConvert(Optional.of(Date.from(Instant.now().minus(Duration.ofDays(30)))))
					.sort(Comparator.comparing(BookDTO::getTitle)));
		}

		return Mono.just("conversions");
	}

	@GetMapping("/conversionsRaw")
	@ResponseBody
	Flux<BookDTO> conversionsRaw() {
		
		return adService.clicksToConvert(Optional.empty())
			.sort(Comparator.comparing(BookDTO::getTitle));
	}

	@PostMapping("/upload")
	Mono<String> upload(@RequestPart(name = "spreadsheet") Flux<FilePart> spreadsheet) {

		return spreadsheet
			.flatMap(file -> {
				log.info("About to parse " + file.filename() + "...");

				Mono<Void> uploadFile = Mono.just(Paths.get(UPLOAD_ROOT, file.filename()).toFile())
					.log("copyfile-fullPath")
					.map(destFile -> {
						try {
							new File(UPLOAD_ROOT).mkdirs();
							destFile.createNewFile();
							return destFile;
						} catch (IOException e) {
							throw new RuntimeException(e);
						}
					})
					.log("copyfile-newFile")
					.flatMap(file::transferTo)
					.log("copyfile-transferTo");

				Mono<Void> ingestSpreadsheet = Mono.just(Paths.get(UPLOAD_ROOT, file.filename()).toFile())
					.map(FileSystemResource::new)
					.map(fileSystemResource -> {
						try {
							return fileSystemResource.getInputStream();
						} catch (IOException e) {
							throw new RuntimeException(e);
						}
					})
					.map(inputStream -> {
						try {
							loaderService.loadSpreadsheet(inputStream);
							return Mono.empty();
						} catch (IOException e) {
							throw new RuntimeException(e);
						}
					})
					.then();

				return uploadFile.then(ingestSpreadsheet);
			})
			.log("upload-done")
			.then(Mono.just("redirect:/"));
	}
}
