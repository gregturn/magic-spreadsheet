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

import static com.greglturnquist.magicspreadsheet.Utils.bestGuess;

import lombok.extern.slf4j.Slf4j;

import java.time.LocalDate;
import java.util.Comparator;

import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;
import reactor.util.function.Tuple2;
import reactor.util.function.Tuples;
import org.springframework.http.codec.multipart.FilePart;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestPart;

/**
 * @author Greg Turnquist
 */
@Controller
@Slf4j
public class MagicSpreadsheetController {

	private final AdTableRepository adTableRepository;
	private final AdService adService;
	private final LoaderService loaderService;
	private final EarningsService earningsService;
	private final BookRepository bookRepository;

	public MagicSpreadsheetController(AdTableRepository adTableRepository,
									  AdService adService, LoaderService loaderService,
									  EarningsService earningsService,
									  BookRepository bookRepository) {

		this.adTableRepository = adTableRepository;
		this.adService = adService;
		this.loaderService = loaderService;
		this.earningsService = earningsService;
		this.bookRepository = bookRepository;
	}

	@GetMapping("/")
	Mono<String> index(Model model) {
		return Mono.just("index");
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

		model.addAttribute("impressions", Flux.range(0, window)
			.map(daysAgo -> LocalDate.now().minusDays(daysAgo))
			.flatMap(date -> Mono.just(date).zipWith(adService.impressions(title, date)))
			.sort(Comparator.comparing(Tuple2::getT1)));

		model.addAttribute("clicks", Flux.range(0, window)
			.map(daysAgo -> LocalDate.now().minusDays(daysAgo))
			.flatMap(date -> Mono.just(date).zipWith(adService.clicks(title, date)))
			.sort(Comparator.comparing(Tuple2::getT1)));

		model.addAttribute("spend", Flux.range(0, window)
			.map(daysAgo -> LocalDate.now().minusDays(daysAgo))
			.flatMap(date -> Mono.just(date).zipWith(adService.spend(title, date)))
			.sort(Comparator.comparing(Tuple2::getT1)));

		model.addAttribute("adCount", Flux.range(0, window)
			.map(daysAgo -> LocalDate.now().minusDays(daysAgo))
			.flatMap(date -> Mono.just(date).zipWith(adService.adCount(title, date)))
			.sort(Comparator.comparing(Tuple2::getT1)));

		model.addAttribute("totalAdSpend", Flux.range(0, window)
			.map(daysAgo -> Tuples.of(LocalDate.now().minusDays(window), LocalDate.now().minusDays(daysAgo)))
			.flatMap(edges -> adService.totalAdSpend(title, edges.getT1(), edges.getT2()))
			.sort(Comparator.comparing(EarningsService.TotalSales::getDate)));

		model.addAttribute("roi", Flux.range(0, window)
			.map(daysAgo -> Tuples.of(LocalDate.now().minusDays(window), LocalDate.now().minusDays(daysAgo)))
			.flatMap(edges -> roi(title, edges.getT1(), edges.getT2()))
			.sort(Comparator.comparing(EarningsService.TotalSales::getDate)));

		return Mono.just("individualReport");
	}

	private Mono<EarningsService.TotalSales> roi(String title, LocalDate beginning, LocalDate end) {

		return earningsService.totalCombinedRevenue(title, beginning, end)
			.map(EarningsService.TotalSales::getTotal)
			.flatMap(revenue -> adService.totalAdSpend(title, beginning, end)
				.map(EarningsService.TotalSales::getTotal)
				.map(adSpend -> new EarningsService.TotalSales(end, 100.0 * (revenue - adSpend) / adSpend)));
	}

	private Mono<EarningsService.TotalSales> roiPerSeries(String seriesName, LocalDate beginning, LocalDate end) {

		return earningsService.totalCombinedRevenuePerSeries(seriesName, beginning, end)
			.map(EarningsService.TotalSales::getTotal)
			.flatMap(revenue -> adService.totalAdSpendPerSeries(seriesName, beginning, end)
				.map(EarningsService.TotalSales::getTotal)
				.map(adSpend -> new EarningsService.TotalSales(end, 100.0 * (revenue - adSpend) / adSpend)));
	}

	@GetMapping("/unlinkedAds")
	Mono<String> unlinkedAds(Model model) {

		model.addAttribute("adLinkForm", new AdBookLink());

		model.addAttribute("books", bookRepository.findAll()
			.map(book -> Tuples.of(book.getTitle(), book.getBookShort())));

		model.addAttribute("royalties", adService.unlinkedRoyalties()
			.map(EbookRoyaltyDataDTO::new));

		model.addAttribute("adTable", adService.unlinkedAds()
			.flatMap(adTableObject -> bestGuess(bookRepository.findAll(), adTableObject).zipWith(Mono.just(adTableObject)))
			.map(objects -> new AdTableDTO(objects.getT2().updateAd(objects.getT1()), objects.getT1().getTitle())));

		model.addAttribute("amsData", adService.unlinkedAmsData()
			.map(AmsDataDTO::new));

		return Mono.just("unlinkedAds");
	}

	@PostMapping("/upload")
	Mono<String> upload(@RequestPart(name = "spreadsheet") Flux<FilePart> spreadsheet) {

		return spreadsheet
			.flatMap(loaderService::importMagicSpreadsheet)
			.log("upload-done")
			.then(Mono.just("redirect:/"));
	}

	@DeleteMapping("/delete-all")
	Mono<String> deleteAll() {

		return loaderService.deleteAll()
			.then(Mono.just("redirect:/"));
	}

}
