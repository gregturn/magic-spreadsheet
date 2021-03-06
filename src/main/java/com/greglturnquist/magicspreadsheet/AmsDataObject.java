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

import lombok.AllArgsConstructor;
import lombok.Data;

import java.time.LocalDate;
import java.util.Optional;

import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.mapping.Document;

/**
 * @author Greg Turnquist
 */
@Data
@AllArgsConstructor
@Document
class AmsDataObject {

	@Id String id;
	int rowNum;
	String status;
	String campaignName;
	String type;
	LocalDate startDate;
	Optional<LocalDate> endDate;
	double budget;
	double totalSpend;
	Optional<Double> impressions;
	Optional<Double> rawImpressions;
	Optional<Double> clicks;
	Optional<Double> rawClicks;
	Optional<Double> averageCpc;
	LocalDate date;
	Optional<LocalDate> previousDate;
	Optional<LocalDate> nextDate;

	public Optional<Double> getRawClicks() {

		if (this.rawClicks == null) {
			return this.clicks;
		} else {
			return this.rawClicks;
		}
	}

	public double getTotalSpend() {
		return this.averageCpc.orElse(0.0) * this.clicks.orElse(0.0);
	}
}
