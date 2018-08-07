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
import java.time.ZoneId;

import org.apache.poi.ss.usermodel.Row;
import reactor.core.publisher.Mono;

/**
 * @author Greg Turnquist
 */
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
			ebookRoyaltyDataObject.getTitle(),
			ebookRoyaltyDataObject.getAuthorName(),
			"",
			"",
			ebookRoyaltyDataObject.getASIN(),
			0.1
		);
	}

	/**
	 * Invert a {@link Boolean} condition, that is wrapped by a {@link Mono}.
	 * 
	 * @param condition
	 * @return
	 */
	static Mono<Boolean> not(Mono<Boolean> condition) {
		return condition
			.map(aBoolean -> !aBoolean);
	}
}
