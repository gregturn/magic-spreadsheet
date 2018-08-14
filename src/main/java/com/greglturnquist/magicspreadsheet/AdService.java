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

import static com.greglturnquist.magicspreadsheet.Utils.mainTitle;

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

	Mono<BookDTO> clicksToConvert(Book book, Optional<LocalDate> date) {

		return Mono.zip(
			adPerformance(book.getTitle(), date),
			unitsSold(book.getTitle(), date),
			totalPagesRead(book.getTitle(), date),
			totalAdSpend(book.getTitle(), date),
			totalEarnings(book.getTitle(), date))

			.map(objects -> new BookDTO(book.getTitle(), objects.getT1(), objects.getT2(), objects.getT3(), book.getKENPC(), objects.getT4(), objects.getT5()));
//			.filter(bookDTO -> !bookDTO.getTotalAdSpend().equals("$0.0"));
//			.filter(bookDTO -> bookDTO.getAdPerformanceStats().getImpressions() > 0.0);
	}

	private Mono<Double> unitsSold(String bookTitle, Optional<LocalDate> date) {

		return date
			.map(after -> ebookRoyaltyRepository.findByTitleLikeAndRoyaltyDateAfter(bookTitle, after))
			.orElse(ebookRoyaltyRepository.findByTitleLike(bookTitle))
			.reduce(0.0, (counter, ebookRoyaltyData) -> counter + ebookRoyaltyData.getNetUnitsSold());
	}

	private Mono<Double> totalPagesRead(String bookTitle, Optional<LocalDate> date) {

		return date
			.map(after -> kenpReadRepository.findByTitleLikeAndOrderDateAfter(bookTitle, after))
			.orElse(kenpReadRepository.findByTitleLike(bookTitle))
			.reduce(0.0, (counter, kenpReadData) -> counter + kenpReadData.getPagesRead());
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

	Mono<Double> totalAdSpend(String bookTitle, Optional<LocalDate> date) {

		return adTableRepository.findByBookTitle(bookTitle)
			.flatMap(adTableObject -> date
				.map(earliestDate -> amsDataRepository.findByCampaignNameAndDateAfter(adTableObject.getCampaignName(), earliestDate))
				.orElse(amsDataRepository.findByCampaignName(adTableObject.getCampaignName())))
			.reduce(0.0, (totalAdSpend, amsDataObject) -> totalAdSpend +
				amsDataObject.getClicks().orElse(0.0) * amsDataObject.getAverageCpc().orElse(0.0));
	}

	Mono<EarningsService.TotalSales> totalAdSpend(String title, LocalDate beginning, LocalDate end) {

		return adTableRepository.findByBookTitle(title)
			.flatMap(adTableObject -> amsDataRepository.findByCampaignNameAndDateBetween(adTableObject.getCampaignName(), beginning, end))
			.reduce(0.0, (totalAdSpend, amsDataObject) -> totalAdSpend +
				amsDataObject.getClicks().orElse(0.0) * amsDataObject.getAverageCpc().orElse(0.0))
			.map(totalAdSpend -> new EarningsService.TotalSales(end, totalAdSpend));
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
