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

import java.text.SimpleDateFormat;
import java.time.LocalDate;
import java.time.format.DateTimeParseException;
import java.util.Arrays;
import java.util.Comparator;
import java.util.Optional;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;
import reactor.util.function.Tuple2;
import reactor.util.function.Tuples;
import org.springframework.data.domain.Sort;
import org.springframework.http.codec.multipart.FilePart;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
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
	private static final SimpleDateFormat SIMPLE_DATE_FORMAT = new SimpleDateFormat("yyyy-MM-dd");

	private final AmsDataRepository amsDataRepository;
	private final AdTableRepository adTableRepository;
	private final AdService adService;
	private final LoaderService loaderService;
	private final EarningsService earningsService;
	private final EbookRoyaltyRepository ebookRoyaltyRepository;

	public MagicSpreadsheetController(AmsDataRepository amsDataRepository,
									  AdTableRepository adTableRepository,
									  AdService adService, LoaderService loaderService,
									  EarningsService earningsService,
									  EbookRoyaltyRepository ebookRoyaltyRepository) {

		this.amsDataRepository = amsDataRepository;
		this.adTableRepository = adTableRepository;
		this.adService = adService;
		this.loaderService = loaderService;
		this.earningsService = earningsService;
		this.ebookRoyaltyRepository = ebookRoyaltyRepository;
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

	@GetMapping("/rawRoyaltyData")
	Mono<String> rawRoyaltyData(@RequestParam(name = "window", required = false) Optional<String> optionalWindow, Model model) {

		String window = optionalWindow.orElse("all");

		model.addAttribute("filterOptions", Arrays.asList(
			new FilterOption("all", "Lifetime", window.equals("all")),
			new FilterOption("90days", "Last 90 days", window.equals("90days")),
			new FilterOption("30days", "Last 30 days", window.equals("30days")),
			new FilterOption("15days", "Last 15 days", window.equals("15days"))
		));

		Sort sortByDateAndCampaignName = Sort.by("royaltyDate", "title");

		if ("all".equals(window)) {

			model.addAttribute("royaltyData", ebookRoyaltyRepository
				.findAll(sortByDateAndCampaignName)
				.map(EbookRoyaltyDataDTO::new));

		} else if ("90days".equals(window)) {

			model.addAttribute("royaltyData", ebookRoyaltyRepository
				.findByRoyaltyDateAfter(LocalDate.now().minusDays(90), sortByDateAndCampaignName)
				.map(EbookRoyaltyDataDTO::new));

		} else if ("30days".equals(window)) {

			model.addAttribute("royaltyData", ebookRoyaltyRepository
				.findByRoyaltyDateAfter(LocalDate.now().minusDays(30), sortByDateAndCampaignName)
				.map(EbookRoyaltyDataDTO::new));

		} else if ("15days".equals(window)) {

			model.addAttribute("royaltyData", ebookRoyaltyRepository
				.findByRoyaltyDateAfter(LocalDate.now().minusDays(15), sortByDateAndCampaignName)
				.map(EbookRoyaltyDataDTO::new));
		}

		return Mono.just("rawRoyaltyData");
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

	@GetMapping("/conversions")
	Mono<String> conversions(@RequestParam(name = "window", required = false) Optional<String> optionalWindow, Model model) {

		String window = optionalWindow.orElse("all");

		model.addAttribute("filterOptions", Arrays.asList(
			new FilterOption("all", "Lifetime", window.equals("all")),
			new FilterOption("90days", "Last 90 days", window.equals("90days")),
			new FilterOption("30days", "Last 30 days", window.equals("30days")),
			new FilterOption("15days", "Last 15 days", window.equals("15days"))
		));

		if ("all".equals(window)) {

			model.addAttribute("conversionData",
				adService.clicksToConvert(Optional.empty())
					.sort(Comparator.comparing(BookDTO::getTitle)));

		} else if ("90days".equals(window)) {

			model.addAttribute("conversionData",
				adService.clicksToConvert(Optional.of(LocalDate.now().minusDays(90)))
					.sort(Comparator.comparing(BookDTO::getTitle)));

		} else if ("30days".equals(window)) {

			model.addAttribute("conversionData",
				adService.clicksToConvert(Optional.of(LocalDate.now().minusDays(30)))
					.sort(Comparator.comparing(BookDTO::getTitle)));

		} else if ("15days".equals(window)) {

			model.addAttribute("conversionData",
				adService.clicksToConvert(Optional.of(LocalDate.now().minusDays(15)))
					.sort(Comparator.comparing(BookDTO::getTitle)));
		}

		return Mono.just("conversions");
	}

	@GetMapping("/individualReport/{title}")
	Mono<String> individualReport(@PathVariable String title, Model model) {

		int window = 40;

		model.addAttribute("window", window);

		model.addAttribute("title", title);

		model.addAttribute("dates", Flux.range(0, window)
			.flatMap(daysAgo -> Mono.just(daysAgo).zipWith(Mono.just(LocalDate.now().minusDays(daysAgo))))
			.sort(Comparator.comparing(Tuple2::getT2))
		);

		model.addAttribute("unitSales", Flux.range(0, window)
			.map(daysAgo -> LocalDate.now().minusDays(daysAgo))
			.flatMap(date -> Mono.just(date).zipWith(earningsService.unitSales(title, date)))
			.sort(Comparator.comparing(Tuple2::getT1))
		);

		model.addAttribute("movingAverageUnitSales", Flux.range(0, window)
			.map(daysAgo -> LocalDate.now().minusDays(daysAgo))
			.flatMap(date -> earningsService.movingAverageUnitsSold(title, date, 7))
			.sort(Comparator.comparing(EarningsService.MovingAverage::getDate)));

		model.addAttribute("totalSales", Flux.range(0, window)
			.map(daysAgo -> Tuples.of(LocalDate.now().minusDays(window), LocalDate.now().minusDays(daysAgo)))
			.flatMap(edges -> earningsService.totalSales(title, edges.getT1(), edges.getT2()))
			.sort(Comparator.comparing(EarningsService.TotalSales::getDate)));

		model.addAttribute("unitRevenue", Flux.range(0, window)
			.map(daysAgo -> LocalDate.now().minusDays(daysAgo))
			.flatMap(date -> Mono.just(date).zipWith(earningsService.unitRevenue(title, date)))
			.sort(Comparator.comparing(Tuple2::getT1))
		);

		model.addAttribute("movingAverageUnitRevenue", Flux.range(0, window)
			.map(daysAgo -> LocalDate.now().minusDays(daysAgo))
			.flatMap(date -> earningsService.movingAverageSalesRevenue(title, date, 7))
			.sort(Comparator.comparing(EarningsService.MovingAverage::getDate)));

		model.addAttribute("totalRevenue", Flux.range(0, window)
			.map(daysAgo -> Tuples.of(LocalDate.now().minusDays(window), LocalDate.now().minusDays(daysAgo)))
			.flatMap(edges -> earningsService.totalRevenue(title, edges.getT1(), edges.getT2()))
			.sort(Comparator.comparing(EarningsService.TotalSales::getDate)));

		model.addAttribute("pageReads", Flux.range(0, window)
			.map(daysAgo -> LocalDate.now().minusDays(daysAgo))
			.flatMap(date -> Mono.just(date).zipWith(earningsService.pagesRead(title, date)))
			.sort(Comparator.comparing(Tuple2::getT1)));

		model.addAttribute("movingAveragePageReads", Flux.range(0, window)
			.map(daysAgo -> LocalDate.now().minusDays(daysAgo))
			.flatMap(date -> earningsService.movingAveragePageReads(title, date, 7))
			.sort(Comparator.comparing(EarningsService.MovingAverage::getDate)));

		model.addAttribute("totalPageReads", Flux.range(0, window)
			.map(daysAgo -> Tuples.of(LocalDate.now().minusDays(window), LocalDate.now().minusDays(daysAgo)))
			.flatMap(edges -> earningsService.totalPageReads(title, edges.getT1(), edges.getT2()))
			.sort(Comparator.comparing(EarningsService.TotalSales::getDate)));

		model.addAttribute("estimatedRevenue", Flux.range(0, window)
			.map(daysAgo -> LocalDate.now().minusDays(daysAgo))
			.flatMap(date -> Mono.just(date).zipWith(earningsService.estimatedRevenue(title, date)))
			.sort(Comparator.comparing(Tuple2::getT1)));

		model.addAttribute("movingAveragePageRevenue", Flux.range(0, window)
			.map(daysAgo -> LocalDate.now().minusDays(daysAgo))
			.flatMap(date -> earningsService.movingAveragePageReadRevenue(title, date, 7))
			.sort(Comparator.comparing(EarningsService.MovingAverage::getDate)));

		model.addAttribute("combinedRevenue", Flux.range(0, window)
			.map(daysAgo -> LocalDate.now().minusDays(daysAgo))
			.flatMap(date -> Mono.just(date).zipWith(earningsService.combinedRevenue(title, date)))
			.sort(Comparator.comparing(Tuple2::getT1)));

		model.addAttribute("totalGrossRevenue", Flux.range(0, window)
			.map(daysAgo -> Tuples.of(LocalDate.now().minusDays(window), LocalDate.now().minusDays(daysAgo)))
			.flatMap(edges -> earningsService.totalCombinedRevenue(title, edges.getT1(), edges.getT2()))
			.sort(Comparator.comparing(EarningsService.TotalSales::getDate)));

		return Mono.just("individualReport");
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
			.flatMap(loaderService::importMagicSpreadsheet)
			.log("upload-done")
			.then(Mono.just("redirect:/"));
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
