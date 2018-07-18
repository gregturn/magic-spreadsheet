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

import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;
import org.springframework.stereotype.Service;

/**
 * @author Greg Turnquist
 */
@Service
@Slf4j
class AdService {

	private static final double KU_RATE = 0.0046;

	private final AmsDataRepository amsDataRepository;
	private final AdTableRepository adTableRepository;
	private final EbookRoyaltyRepository ebookRoyaltyRepository;
	private final BookRepository bookRepository;
	private final KenpReadRepository kenpReadRepository;


	AdService(AmsDataRepository amsDataRepository,
			  AdTableRepository adTableRepository,
			  EbookRoyaltyRepository ebookRoyaltyRepository,
			  BookRepository bookRepository,
			  KenpReadRepository kenpReadRepository) {

		this.amsDataRepository = amsDataRepository;
		this.adTableRepository = adTableRepository;
		this.ebookRoyaltyRepository = ebookRoyaltyRepository;
		this.bookRepository = bookRepository;
		this.kenpReadRepository = kenpReadRepository;
	}

	Flux<BookDTO> clicksToConvert() {

		return bookRepository.findAll()
			.flatMap(this::clicksToConvert);
	}

	private Mono<BookDTO> clicksToConvert(Book book) {

		return Mono.zip(
			adPerformance(book.getTitle()), unitsSold(book.getTitle()),
			totalPagesRead(book.getTitle()), totalAdSpend(book.getTitle()),
			totalEarnings(book.getTitle()))

			.map(objects -> new BookDTO(book.getTitle(), objects.getT1(), objects.getT2(), objects.getT3(), book.getKENPC(), objects.getT4(), objects.getT5()))
			.filter(bookDTO -> bookDTO.getAdPerformanceStats().getImpressions() > 0.0);
	}

	private Mono<Double> unitsSold(String bookTitle) {
		
		return ebookRoyaltyRepository.findByTitle(bookTitle)
			.reduce(0.0, (counter, ebookRoyaltyData) -> counter + ebookRoyaltyData.getNetUnitsSold());
	}

	private Mono<Double> totalPagesRead(String bookTitle) {

		return kenpReadRepository.findByTitle(bookTitle)
			.reduce(0.0, (counter, kenpReadData) -> counter + kenpReadData.getPagesRead());
	}

	private Mono<Double> totalClicks(String bookTitle) {

		return adTableRepository.findByBookTitle(bookTitle)
			.flatMap(adTableObject -> amsDataRepository.findByCampaignName(adTableObject.getCampaignName()))
			.reduce(0.0, (counter, amsData) -> amsData.getClicks()
				.map(theseImpressions -> counter + theseImpressions)
				.orElse(counter));
	}

	private Mono<Double> totalImpressions(String bookTitle) {

		return adTableRepository.findByBookTitle(bookTitle)
			.flatMap(adTableObject -> amsDataRepository.findByCampaignName(adTableObject.getCampaignName()))
			.reduce(0.0, (counter, amsDataObject) -> amsDataObject.getImpressions()
				.map(theseImpressions -> counter + theseImpressions)
				.orElse(counter));
	}

	private Mono<AdPerformanceStats> adPerformance(String bookTitle) {

		return adTableRepository.findByBookTitle(bookTitle)
			.flatMap(adTableObject -> amsDataRepository.findByCampaignName(adTableObject.getCampaignName()))
			.reduce(new AdPerformanceStats(0.0, 0.0), (adPerformanceStats, amsDataObject) ->
				new AdPerformanceStats(
					amsDataObject.getImpressions()
						.map(theseImpressions -> adPerformanceStats.getImpressions() + theseImpressions)
						.orElse(adPerformanceStats.getImpressions()),
					amsDataObject.getClicks()
						.map(theseClicks -> adPerformanceStats.getClicks() + theseClicks)
						.orElse(adPerformanceStats.getClicks())));
	}

	private Mono<Double> totalAdSpend(String bookTitle) {

		return adTableRepository.findByBookTitle(bookTitle)
			.flatMap(adTableObject -> amsDataRepository.findByCampaignName(adTableObject.getCampaignName()))
			.reduce(0.0, (totalAdSpend, amsDataObject) -> totalAdSpend + amsDataObject.getAverageCpc()
				.map(averageCpc -> averageCpc * amsDataObject.getClicks().orElse(0.0))
				.orElse(0.0));
	}

	private Mono<Double> totalEarnings(String bookTitle) {

		Mono<Double> totalRoyalties = ebookRoyaltyRepository.findByTitle(bookTitle)
			.reduce(0.0, (royalties, ebookRoyaltyDataObject) -> royalties + ebookRoyaltyDataObject.getRoyalty());

		Mono<Double> totalPagesRead = kenpReadRepository.findByTitle(bookTitle)
			.reduce(0.0, (pagesRead, kenpReadData) -> pagesRead + kenpReadData.getPagesRead());

		return Mono.zip(totalRoyalties, totalPagesRead, (royalties, pagesRead) -> {
			log.info(bookTitle + ": Totaling up $" + royalties + " along with " + pagesRead + " pages read");
			return royalties + pagesRead * KU_RATE;
		});
	}
}