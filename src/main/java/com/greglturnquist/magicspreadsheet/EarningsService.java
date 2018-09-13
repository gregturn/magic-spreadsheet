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

import static reactor.function.TupleUtils.function;

import lombok.RequiredArgsConstructor;
import lombok.Value;
import lombok.extern.slf4j.Slf4j;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.stream.Collectors;

import reactor.core.publisher.Mono;
import reactor.util.function.Tuples;
import org.springframework.stereotype.Service;

/**
 * @author Greg Turnquist
 */
@Service
@Slf4j
class EarningsService {

	private final EbookRoyaltyRepository ebookRoyaltyRepository;
	private final KenpReadRepository kenpReadRepository;
	private final BookRepository bookRepository;

	EarningsService(EbookRoyaltyRepository ebookRoyaltyRepository,
					KenpReadRepository kenpReadRepository,
					BookRepository bookRepository) {

		this.ebookRoyaltyRepository = ebookRoyaltyRepository;
		this.kenpReadRepository = kenpReadRepository;
		this.bookRepository = bookRepository;
	}

	Mono<Double> unitSales(String title, LocalDate royaltyDate) {

		return ebookRoyaltyRepository.findByTitleAndRoyaltyDate(title, royaltyDate)
			.map(EbookRoyaltyDataObject::getNetUnitsSold)
			.reduce(0.0, (total, value) -> total + value)
			.switchIfEmpty(Mono.just(0.0));
	}

	Mono<Double> unitRevenue(String title, LocalDate royaltyDate) {

		return ebookRoyaltyRepository.findByTitleAndRoyaltyDate(title, royaltyDate)
			.map(EbookRoyaltyDataObject::getRoyalty)
			.reduce(0.0, (total, value) -> total + value)
			.switchIfEmpty(Mono.just(0.0));
	}

	Mono<Double> pagesRead(String title, LocalDate date) {

		return kenpReadRepository.findByTitleAndOrderDate(title, date)
			.map(KenpReadDataObject::getPagesRead)
			.switchIfEmpty(Mono.just(0.0));
	}

	Mono<Double> estimatedRevenue(String title, LocalDate date) {

		return kenpReadRepository.findByTitleAndOrderDate(title, date)
			.map(KenpReadDataObject::getPagesRead)
			.map(EarningsService::pageReadsToDollars)
			.switchIfEmpty(Mono.just(0.0));
	}

	Mono<MovingAverage> movingAverageUnitsSold(String title, LocalDate date, int lookbackWindowSize) {

		return ebookRoyaltyRepository.findByTitleAndRoyaltyDateBetween(title, date.minusDays(lookbackWindowSize), date)
			.collectList()
			.map(ebookRoyaltyDataObjects -> {

				if (ebookRoyaltyDataObjects.size() == lookbackWindowSize) {
					
					return new MovingAverage<>(date, ebookRoyaltyDataObjects, ebookRoyaltyDataObjects.stream()
						.map(EbookRoyaltyDataObject::getNetUnitsSold)
						.collect(Collectors.averagingDouble(value -> value)));
				} else {

					List<Double> newList = new ArrayList<>(lookbackWindowSize);
					
					newList.addAll(ebookRoyaltyDataObjects.stream()
						.map(EbookRoyaltyDataObject::getNetUnitsSold)
						.collect(Collectors.toList()));

					for (int i=0; i < lookbackWindowSize - ebookRoyaltyDataObjects.size(); i++) {
						newList.add(0.0);
					}

					return new MovingAverage<>(date, ebookRoyaltyDataObjects, newList.stream()
						.collect(Collectors.averagingDouble(value -> value)));
				}
			});
	}

	Mono<MovingAverage> movingAverageSalesRevenue(String title, LocalDate date, int lookbackWindowSize) {

		return ebookRoyaltyRepository.findByTitleAndRoyaltyDateBetween(title, date.minusDays(lookbackWindowSize), date)
			.collectList()
			.map(ebookRoyaltyDataObjects -> {

				if (ebookRoyaltyDataObjects.size() == lookbackWindowSize) {

					return new MovingAverage<>(date, ebookRoyaltyDataObjects, ebookRoyaltyDataObjects.stream()
						.map(EbookRoyaltyDataObject::getRoyalty)
						.collect(Collectors.averagingDouble(value -> value)));
				} else {

					List<Double> newList = new ArrayList<>(lookbackWindowSize);

					newList.addAll(ebookRoyaltyDataObjects.stream()
						.map(EbookRoyaltyDataObject::getRoyalty)
						.collect(Collectors.toList()));

					for (int i=0; i < lookbackWindowSize - ebookRoyaltyDataObjects.size(); i++) {
						newList.add(0.0);
					}

					log.info("About to average " + newList);

					return new MovingAverage<>(date, ebookRoyaltyDataObjects, newList.stream()
						.collect(Collectors.averagingDouble(value -> value)));
				}
			});
	}

	Mono<TotalSales> totalSales(String title, LocalDate beginning, LocalDate end) {
		
		return ebookRoyaltyRepository.findByTitleAndRoyaltyDateBetween(title, beginning, end)
			.reduce(0.0, (total, ebookRoyaltyDataObject) -> total += ebookRoyaltyDataObject.getNetUnitsSold())
			.map(aDouble -> new TotalSales(end, aDouble));
	}

	Mono<TotalSales> totalRevenue(String title, LocalDate beginning, LocalDate end) {

		return ebookRoyaltyRepository.findByTitleAndRoyaltyDateBetween(title, beginning, end)
			.reduce(0.0, (total, ebookRoyaltyDataObject) -> total += ebookRoyaltyDataObject.getRoyalty())
			.map(aDouble -> new TotalSales(end, aDouble));
	}

	Mono<TotalSales> totalRevenuePerSeries(String seriesName, LocalDate beginning, LocalDate end) {

		return bookRepository.findBySeries(seriesName)
			.flatMap(book -> totalRevenue(book.getTitle(), beginning, end))
			.reduce(new TotalSales(end, 0.0),
				(totalSales, totalSales2) -> new TotalSales(end, totalSales.getTotal() + totalSales2.getTotal()));
	}

	Mono<TotalSales> totalPageReads(String title, LocalDate beginning, LocalDate end) {

		return kenpReadRepository.findByTitleAndOrderDateBetween(title, beginning, end)
			.reduce(0.0, (total, kenpReadDataObject) -> total += kenpReadDataObject.getPagesRead())
			.map(aDouble -> new TotalSales(end, aDouble));
	}

	Mono<TotalSales> totalPageRevenue(String title, LocalDate beginning, LocalDate end) {

		return totalPageReads(title, beginning, end)
			.map(TotalSales::getTotal)
			.map(EarningsService::pageReadsToDollars)
			.map(aDouble -> new TotalSales(end, aDouble));
	}

	Mono<TotalSales> totalPageRevenuePerSeries(String seriesName, LocalDate beginning, LocalDate end) {

		return bookRepository.findBySeries(seriesName)
			.flatMap(book -> totalPageRevenue(book.getTitle(), beginning, end))
			.reduce(new TotalSales(end, 0.0),
				(totalSales, totalSales2) -> new TotalSales(end, totalSales.getTotal() + totalSales2.getTotal()));
	}

	Mono<TotalSales> totalCombinedRevenue(String title, LocalDate beginning, LocalDate end) {

		return totalRevenue(title, beginning, end)
			.flatMap(ebookSales -> totalPageRevenue(title, beginning, end)
				.map(pageReadSales -> Tuples.of(ebookSales.getTotal(), pageReadSales.getTotal())))
			.map(function((ebookRevenue, pageReadRevenue) -> ebookRevenue + pageReadRevenue))
			.map(totalRevenue -> new TotalSales(end, totalRevenue));
	}

	Mono<TotalSales> totalCombinedRevenuePerSeries(String seriesName, LocalDate beginning, LocalDate end) {

		return totalRevenuePerSeries(seriesName, beginning, end)
			.flatMap(ebookSales -> totalPageRevenuePerSeries(seriesName, beginning, end)
				.map(pageReadSales -> Tuples.of(ebookSales.getTotal(), pageReadSales.getTotal())))
			.map(function((ebookRevenue, pageReadRevenue) -> ebookRevenue + pageReadRevenue))
			.map(aDouble -> new TotalSales(end, aDouble));
	}

	Mono<MovingAverage> movingAveragePageReads(String title, LocalDate date, int lookbackWindowSize) {

		return kenpReadRepository.findByTitleAndOrderDateBetween(title, date.minusDays(lookbackWindowSize), date)
			.collectList()
			.map(kenpReadDataObjects -> {

				if (kenpReadDataObjects.size() == lookbackWindowSize) {

					return new MovingAverage<>(date, kenpReadDataObjects, kenpReadDataObjects.stream()
						.map(KenpReadDataObject::getPagesRead)
						.collect(Collectors.averagingDouble(value -> value)));
				} else {

					List<Double> newList = new ArrayList<>(lookbackWindowSize);

					newList.addAll(kenpReadDataObjects.stream()
						.map(KenpReadDataObject::getPagesRead)
						.collect(Collectors.toList()));

					for (int i=0; i < lookbackWindowSize - kenpReadDataObjects.size(); i++) {
						newList.add(0.0);
					}

					log.info("About to average " + newList);

					return new MovingAverage<>(date, kenpReadDataObjects, newList.stream()
						.collect(Collectors.averagingDouble(value -> value)));
				}
			});
	}

	Mono<MovingAverage> movingAveragePageReadRevenue(String title, LocalDate date, int lookbackWindowSize) {

		return movingAveragePageReads(title, date, lookbackWindowSize)
			.map(MovingAverage::getAverage)
			.map(EarningsService::pageReadsToDollars)
			.map(aDouble -> new MovingAverage<>(date, Collections.emptyList(), aDouble));
	}

	Mono<Double> combinedRevenue(String title, LocalDate date) {

		return unitRevenue(title, date)
			.flatMap(unitRevenue -> pagesRead(title, date)
				.map(EarningsService::pageReadsToDollars)
				.map(pageReadRevenue -> pageReadRevenue + unitRevenue));
	}

	static double pageReadsToDollars(double pageReads) {
		return pageReads * AdService.KU_RATE;
	}

	@Value
	@RequiredArgsConstructor
	static class MovingAverage<T> {

		private final LocalDate date;
		private final List<T> raw;
		private final double average;
	}

	@Value
	@RequiredArgsConstructor
	static class TotalSales {

		private final LocalDate date;
		private final double total;
	}
}
