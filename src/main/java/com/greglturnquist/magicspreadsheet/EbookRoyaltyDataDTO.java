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

import lombok.RequiredArgsConstructor;

import java.time.LocalDate;

/**
 * @author Greg Turnquist
 */
@RequiredArgsConstructor
public class EbookRoyaltyDataDTO {

	private final EbookRoyaltyDataObject ebookRoyaltyDataObject;

	public LocalDate getRoyaltyDate() {
		return this.ebookRoyaltyDataObject.getRoyaltyDate();
	}

	public String getTitle() {
		return this.ebookRoyaltyDataObject.getTitle();
	}

	public String getAuthorName() {
		return this.ebookRoyaltyDataObject.getAuthorName();
	}

	public String getASIN() {
		return this.ebookRoyaltyDataObject.getASIN();
	}

	public double getNetUnitsSold() {
		return this.ebookRoyaltyDataObject.getNetUnitsSold();
	}

	public double getRoyalty() {
		return this.ebookRoyaltyDataObject.getRoyalty();
	}

	public String getCurrency() {
		return this.ebookRoyaltyDataObject.getCurrency();
	}

}
