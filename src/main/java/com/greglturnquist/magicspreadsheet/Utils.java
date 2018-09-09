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
import java.time.ZoneId;
import java.util.List;

import org.apache.poi.ss.usermodel.Row;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;
import reactor.util.function.Tuple2;
import reactor.util.function.Tuples;

/**
 * @author Greg Turnquist
 */
@Slf4j
class Utils {

	static LocalDate dateValue(int index, Row row) {
		return row.getCell(index).getDateCellValue().toInstant().atZone(ZoneId.systemDefault()).toLocalDate();
	}

	static AdTableObject amsDataToAdData(AmsDataObject amsDataObject) {

		return new AdTableObject(
			null,
			-1,
			amsDataObject.getCampaignName(),
			amsDataObject.getType(),
			amsDataObject.getStartDate(),
			amsDataObject.getEndDate(),
			amsDataObject.getBudget(),
			null,
			null
		);
	}


	static Book royaltyToBook(EbookRoyaltyDataObject ebookRoyaltyDataObject) {

		return new Book(
			null,
			-1,
			-1,
			mainTitle(ebookRoyaltyDataObject.getTitle()),
			subTitle(ebookRoyaltyDataObject.getTitle()),
			ebookRoyaltyDataObject.getAuthorName(),
			"",
			"",
			-1,
			ebookRoyaltyDataObject.getASIN(),
			0.1
		);
	}

	static Mono<Book> bestGuess(Flux<Book> books, AdTableObject ad) {

		return books
			.filter(ad::referencesBook)
			.next()
			.switchIfEmpty(Mono.just(Book.NONE));
	}

	static String mainTitle(String longTitle) {
		return longTitle.split(":")[0];
	}

	static String subTitle(String longTitle) {
		
		if (longTitle.contains(":")) {
			return longTitle.split(":")[1].trim();
		} else {
			return "";
		}
	}

	static Mono<List<Tuple2<Double, LocalDate>>> clicksPerSale(EbookRoyaltyRepository royaltyRepository, AmsDataRepository amsDataRepository, String title) {

		return royaltyRepository.findByTitleLike(title)
			.map(EbookRoyaltyDataObject::getRoyaltyDate)
			.buffer(2, 1)
			.flatMap(interval -> {
				if (interval.size() == 2) {
					return Mono.just(Tuples.of(interval.get(0), interval.get(1)));
				} else {
					return Mono.empty();
				}
			})
			.flatMap(range -> {

				LocalDate date1 = range.getT1();
				LocalDate date2 = range.getT2();

				LocalDate earlier = date1.isBefore(date2) ? date1 : date2;
				LocalDate later = date2.isAfter(date1) ? date2 : date1;

				return clicksPerTitlePerRange(amsDataRepository, title, earlier, later).zipWith(Mono.just(later));
			})
			.collectList();
	}

	/**
	 * For a given title and range of dates, tally up all clicks.
	 * 
	 * @param repository
	 * @param title
	 * @param date1
	 * @param date2
	 * @return
	 */
	static Mono<Double> clicksPerTitlePerRange(AmsDataRepository repository, String title, LocalDate date1, LocalDate date2) {

		return repository.findByCampaignNameAndDateBetween(title, date1, date2)
			.map(amsDataObject -> amsDataObject.getClicks().orElse(0.0))
			.reduce(0.0, (subtotal, clicks) -> subtotal + clicks);
	}

	static Double unitsSoldViaPageReads(Double kenpc, Double totalPageReads) {

		if (kenpc >= 1.0) {
			return totalPageReads / kenpc;
		} else {
			return 0.0;
		}
	}
}
