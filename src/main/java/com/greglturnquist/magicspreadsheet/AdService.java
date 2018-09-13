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

import static com.greglturnquist.magicspreadsheet.Utils.*;
import static reactor.function.TupleUtils.*;

import lombok.extern.slf4j.Slf4j;

import java.time.LocalDate;
import java.util.Optional;

import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;
import org.springframework.data.mongodb.core.ReactiveMongoOperations;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

/**
 * @author Greg Turnquist
 */
@Service
@Slf4j
class AdService {

	static final double KU_RATE = 0.0046;

	private final AmsDataRepository amsDataRepository;
	private final AdTableRepository adTableRepository;
	private final EbookRoyaltyRepository ebookRoyaltyRepository;
	private final BookRepository bookRepository;
	private final KenpReadRepository kenpReadRepository;

	private final ReactiveMongoOperations operations;


	AdService(AmsDataRepository amsDataRepository,
			  AdTableRepository adTableRepository,
			  EbookRoyaltyRepository ebookRoyaltyRepository,
			  BookRepository bookRepository,
			  KenpReadRepository kenpReadRepository,
			  ReactiveMongoOperations operations) {

		this.amsDataRepository = amsDataRepository;
		this.adTableRepository = adTableRepository;
		this.ebookRoyaltyRepository = ebookRoyaltyRepository;
		this.bookRepository = bookRepository;
		this.kenpReadRepository = kenpReadRepository;
		this.operations = operations;
	}

	Flux<BookDTO> clicksToConvert(Optional<LocalDate> date) {

		return bookRepository.findAll()
			.flatMap(book -> clicksToConvert(book, date));
	}

	Flux<SeriesDTO> clicksToConvertPerSeries(Optional<LocalDate> date) {

		return bookRepository.findAll()
			.map(Book::getSeries)
			.filter(StringUtils::hasText)
			.distinct()
			.flatMap(seriesName -> clicksToConvertPerSeries(seriesName, date));
	}

	Mono<BookDTO> clicksToConvert(Book book, Optional<LocalDate> date) {

		return Mono.zip(
			adPerformance(book.getTitle(), date),
			unitsSold(book.getTitle(), date),
			totalPagesRead(book.getTitle(), date),
			totalUnitsSoldViaPageReads(book.getTitle(), date),
			totalAdSpend(book.getTitle(), date),
			totalEarnings(book.getTitle(), date),
			seriesReadThrough(book, date))

			.map(function((adPerformance, unitsSold, totalPagedRead, unitsSoldViaPageReads, adSpend, earnings, seriesReadThrough) -> new BookDTO(
				book.getTitle(),
				adPerformance,
				unitsSold,
				totalPagedRead,
				unitsSoldViaPageReads,
				adSpend,
				earnings,
				seriesReadThrough
			)));
	}

	Mono<SeriesDTO> clicksToConvertPerSeries(String seriesName, Optional<LocalDate> date) {

		return Mono.zip(
			adPerformancePerSeries(seriesName, date),
			unitsSoldPerSeries(seriesName, date),
			totalPagesReadPerSeries(seriesName, date),
			totalUnitsSoldViaPageReadsPerSeries(seriesName, date),
			totalAdSpendPerSeries(seriesName, date),
			totalEarningsPerSeries(seriesName, date))

			.map(function((adPerformance, unitsSold, totalPagesRead, unitsSoldViaPageReads, adSpend, earnings) -> new SeriesDTO(
				seriesName,
				adPerformance,
				unitsSold,
				totalPagesRead,
				unitsSoldViaPageReads,
				adSpend,
				earnings
			)));
	}

	private Mono<Double> seriesReadThrough(Book book, Optional<LocalDate> date) {

		return Optional.ofNullable(book.getSeriesNumber())
			.map(seriesNumber -> bookRepository.findBySeriesAndSeriesNumber(book.getSeries(), seriesNumber+1))
			.orElse(Mono.empty())
			.flatMap(nextInSeries ->
				Mono.zip(
					Mono.just(book),
					unitsSold(book.getTitle(), date),
					totalPagesRead(book.getTitle(), date),
					Mono.just(nextInSeries),
					unitsSold(nextInSeries.getTitle(), date),
					totalPagesRead(nextInSeries.getTitle(), date)
				))
			.flatMap(function((firstBook, unitsSoldOfFirstBook, totalPagesReadOfFirstBook,
							   secondBook, unitsSoldOfSecondBook, totalPagesReadOfSecondBook) ->
				Mono.just(
					(unitsSoldOfSecondBook + unitsSoldViaPageReads(secondBook.getKENPC(), totalPagesReadOfSecondBook)) /
					(unitsSoldOfFirstBook + unitsSoldViaPageReads(firstBook.getKENPC(), totalPagesReadOfFirstBook)))))
			.switchIfEmpty(Mono.just(0.0));
	}

	private Mono<Double> unitsSold(String bookTitle, Optional<LocalDate> date) {

		return date
			.map(after -> ebookRoyaltyRepository.findByTitleLikeAndRoyaltyDateAfter(bookTitle, after))
			.orElse(ebookRoyaltyRepository.findByTitleLike(bookTitle))
			.reduce(0.0, (counter, ebookRoyaltyData) -> counter + ebookRoyaltyData.getNetUnitsSold());
	}

	private Mono<Double> unitsSoldPerSeries(String seriesName, Optional<LocalDate> date) {

		return bookRepository.findBySeries(seriesName)
			.map(Book::getTitle)
			.flatMap(title -> unitsSold(title, date))
			.reduce(0.0, (total, unitsSold) -> total + unitsSold);
	}

	private Mono<Double> totalPagesRead(String bookTitle, Optional<LocalDate> date) {

		return date
			.map(after -> kenpReadRepository.findByTitleLikeAndOrderDateAfter(bookTitle, after))
			.orElse(kenpReadRepository.findByTitleLike(bookTitle))
			.reduce(0.0, (counter, kenpReadData) -> counter + kenpReadData.getPagesRead());
	}

	private Mono<Double> totalPagesReadPerSeries(String seriesName, Optional<LocalDate> date) {

		return bookRepository.findBySeries(seriesName)
			.map(book -> book.getTitle())
			.flatMap(title -> totalPagesRead(title, date))
			.reduce(0.0, (total, pagesRead) -> total + pagesRead);
	}

	private Mono<Double> totalUnitsSoldViaPageReads(String bookTitle, Optional<LocalDate> date) {

		return totalPagesRead(bookTitle, date)
			.flatMap(pagesRead -> bookRepository.findByTitle(bookTitle)
				.map(book -> book.getKENPC())
				.map(kenpc -> Utils.unitsSoldViaPageReads(kenpc, pagesRead)));
	}

	private Mono<Double> totalUnitsSoldViaPageReadsPerSeries(String seriesName, Optional<LocalDate> date) {

		return bookRepository.findBySeries(seriesName)
			.map(Book::getTitle)
			.flatMap(title -> totalUnitsSoldViaPageReads(title, date))
			.reduce(0.0, (total, unitsSold) -> total + unitsSold);
	}

	private Mono<Double> totalClicks(String bookTitle, Optional<LocalDate> date) {

		return adTableRepository.findByBookTitle(bookTitle)
			.flatMap(adTableObject -> date
				.map(earliestDate -> amsDataRepository.findByCampaignNameAndDateAfter(adTableObject.getCampaignName(), earliestDate))
				.orElse(amsDataRepository.findByCampaignName(adTableObject.getCampaignName())))
			.reduce(0.0, (counter, amsData) -> amsData.getClicks()
				.map(theseImpressions -> counter + theseImpressions)
				.orElse(counter));
	}

	private Mono<Double> totalImpressions(String bookTitle, Optional<LocalDate> date) {

		return adTableRepository.findByBookTitle(bookTitle)
			.flatMap(adTableObject -> date
				.map(earliestDate -> amsDataRepository.findByCampaignNameAndDateAfter(adTableObject.getCampaignName(), earliestDate))
				.orElse(amsDataRepository.findByCampaignName(adTableObject.getCampaignName())))
			.reduce(0.0, (counter, amsDataObject) -> amsDataObject.getImpressions()
				.map(theseImpressions -> counter + theseImpressions)
				.orElse(counter));
	}

	private Mono<AdPerformanceStats> adPerformance(String bookTitle, Optional<LocalDate> date) {

		return adTableRepository.findByBookTitle(bookTitle)
			.flatMap(adTableObject -> date
				.map(earliestDate -> amsDataRepository.findByCampaignNameAndDateAfter(adTableObject.getCampaignName(), earliestDate))
				.orElse(amsDataRepository.findByCampaignName(adTableObject.getCampaignName())))
			.reduce(new AdPerformanceStats(0.0, 0.0), (adPerformanceStats, amsDataObject) ->
				new AdPerformanceStats(
					amsDataObject.getImpressions()
						.map(theseImpressions -> adPerformanceStats.getImpressions() + theseImpressions)
						.orElse(adPerformanceStats.getImpressions()),
					amsDataObject.getClicks()
						.map(theseClicks -> adPerformanceStats.getClicks() + theseClicks)
						.orElse(adPerformanceStats.getClicks())));
	}

	private Mono<AdPerformanceStats> adPerformancePerSeries(String seriesName, Optional<LocalDate> date) {

		return bookRepository.findBySeries(seriesName)
			.map(Book::getTitle)
			.flatMap(title -> adPerformance(title, date))
			.reduce(new AdPerformanceStats(0.0, 0.0),
				(adPerformanceStats, adPerformanceStats2) -> new AdPerformanceStats(
					adPerformanceStats.getImpressions() + adPerformanceStats2.getImpressions(),
					adPerformanceStats.getClicks() + adPerformanceStats2.getClicks()));
	}

	Mono<Double> totalAdSpend(String bookTitle, Optional<LocalDate> date) {

		return adTableRepository.findByBookTitle(bookTitle)
			.flatMap(adTableObject -> date
				.map(earliestDate -> amsDataRepository.findByCampaignNameAndDateAfter(adTableObject.getCampaignName(), earliestDate))
				.orElse(amsDataRepository.findByCampaignName(adTableObject.getCampaignName())))
			.reduce(0.0, (totalAdSpend, amsDataObject) -> totalAdSpend +
				amsDataObject.getClicks().orElse(0.0) * amsDataObject.getAverageCpc().orElse(0.0));
	}

	Mono<Double> totalAdSpendPerSeries(String seriesName, Optional<LocalDate> date) {

		return bookRepository.findBySeries(seriesName)
			.map(Book::getTitle)
			.flatMap(title -> totalAdSpend(title, date))
			.reduce(0.0, (total, adSpend) -> total + adSpend);
	}

	Mono<EarningsService.TotalSales> totalAdSpend(String title, LocalDate beginning, LocalDate end) {

		return adTableRepository.findByBookTitle(title)
			.flatMap(adTableObject -> amsDataRepository.findByCampaignNameAndDateBetween(adTableObject.getCampaignName(), beginning, end))
			.reduce(0.0, (totalAdSpend, amsDataObject) -> totalAdSpend +
				amsDataObject.getClicks().orElse(0.0) * amsDataObject.getAverageCpc().orElse(0.0))
			.map(totalAdSpend -> new EarningsService.TotalSales(end, totalAdSpend));
	}

	Mono<EarningsService.TotalSales> totalAdSpendPerSeries(String seriesName, LocalDate beginning, LocalDate end) {

		return bookRepository.findBySeries(seriesName)
			.flatMap(book -> totalAdSpend(book.getTitle(), beginning, end))
			.reduce(new EarningsService.TotalSales(end, 0.0),
				(totalSales, totalSales2) -> new EarningsService.TotalSales(end, totalSales.getTotal() + totalSales2.getTotal()));
	}

	private Mono<Double> totalEarnings(String bookTitle, Optional<LocalDate> date) {

		Mono<Double> totalRoyalties = date
			.map(date1 -> ebookRoyaltyRepository.findByTitleLikeAndRoyaltyDateAfter(bookTitle, date1))
			.orElse(ebookRoyaltyRepository.findByTitleLike(bookTitle))
			.reduce(0.0, (royalties, ebookRoyaltyDataObject) -> royalties + ebookRoyaltyDataObject.getRoyalty());

		Mono<Double> totalPagesRead = date
			.map(date1 -> kenpReadRepository.findByTitleLikeAndOrderDateAfter(bookTitle, date1))
			.orElse(kenpReadRepository.findByTitleLike(bookTitle))
			.reduce(0.0, (pagesRead, kenpReadData) -> pagesRead + kenpReadData.getPagesRead());

		return Mono.zip(totalRoyalties, totalPagesRead, (royalties, pagesRead) -> {
			log.info(bookTitle + ": Totaling up $" + royalties + " along with " + pagesRead + " pages read");
			return royalties + pagesRead * KU_RATE;
		});
	}

	private Mono<Double> totalEarningsPerSeries(String seriesName, Optional<LocalDate> date) {

		return bookRepository.findBySeries(seriesName)
			.map(Book::getTitle)
			.flatMap(title -> totalEarnings(title, date))
			.reduce(0.0, (total, earnings) -> total + earnings);
	}

	Mono<Double> impressions(String title, LocalDate date) {

		return adTableRepository.findByBookTitle(title)
			.flatMap(adTableObject -> amsDataRepository.findByCampaignNameAndDate(adTableObject.getCampaignName(), date))
			.reduce(0.0, (total, amsDataObject) -> total + amsDataObject.getImpressions().orElse(0.0));
	}

	Mono<Double> clicks(String title, LocalDate date) {

		return adTableRepository.findByBookTitle(title)
			.flatMap(adTableObject -> amsDataRepository.findByCampaignNameAndDate(adTableObject.getCampaignName(), date))
			.reduce(0.0, (total, amsDataObject) -> total + amsDataObject.getClicks().orElse(0.0));
	}

	Mono<Double> spend(String title, LocalDate date) {

		return adTableRepository.findByBookTitle(title)
			.flatMap(adTableObject -> amsDataRepository.findByCampaignNameAndDate(adTableObject.getCampaignName(), date))
			.reduce(0.0, (total, amsDataObject) -> total +
				amsDataObject.getAverageCpc().orElse(0.0) * amsDataObject.getClicks().orElse(0.0));
	}

	Mono<Long> adCount(String title, LocalDate date) {

		return adTableRepository.findByBookTitle(title)
			.flatMap(adTableObject -> amsDataRepository.findByCampaignNameAndDate(adTableObject.getCampaignName(), date))
			.filter(amsDataObject -> amsDataObject.getImpressions().orElse(0.0) >= 1000.0)
			.count();
	}

	Flux<AmsDataObject> unlinkedAmsData() {

		return adTableRepository.findAll()
			.map(AdTableObject::getCampaignName)
			.collectList()
			.flatMapMany(campaigns -> amsDataRepository.findAll()
				.filter(amsDataObject -> !campaigns.contains(amsDataObject.getCampaignName())))
			.distinct(AmsDataObject::getCampaignName);
	}

	Flux<AdTableObject> unlinkedAds() {

		return adTableRepository.findAll()
			.filter(adTableObject -> StringUtils.isEmpty(adTableObject.getBookTitle()));
	}

	Flux<EbookRoyaltyDataObject> unlinkedRoyalties() {

		return bookRepository.findAll()
			.map(Book::getTitle)
			.collectList()
			.flatMapMany(titles -> ebookRoyaltyRepository.findAll()
				.filter(ebookRoyaltyDataObject -> !titles.contains(mainTitle(ebookRoyaltyDataObject.getTitle()))));
	}
}
