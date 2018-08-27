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
public class AmsDataDTO {

	private final AmsDataObject object;

	public int getRowNum() {
		return this.object.getRowNum();
	}

	public String getStatus() {
		return this.object.getStatus();
	}

	public String getCampaignName() {
		return this.object.getCampaignName();
	}

	public String getId() {
		return this.object.getId();
	}

	public String getImpressions() {
		return this.object.getImpressions()
			.map(Object::toString)
			.orElse("");
	}

	public String getRawImpressions() {
		return this.object.getRawImpressions()
			.map(Object::toString)
			.orElse("");
	}

	public String getClicks() {
		return this.object.getClicks()
			.map(Object::toString)
			.orElse("");
	}

	public String getRawClicks() {
		return this.object.getRawClicks()
			.map(Object::toString)
			.orElse("");
	}

	public String getAverageCpc() {
		return this.object.getAverageCpc()
			.map(Object::toString)
			.orElse("");
	}

	public String getTotalSpend() {
		return "$" + this.object.getTotalSpend();
	}

	public LocalDate getDate() {
		return this.object.getDate();
	}

	public String getPreviousDate() {

		return this.object.getPreviousDate()
			.map(LocalDate::toString)
			.orElse("");
	}

	public String getNextDate() {

		return this.object.getNextDate()
			.map(LocalDate::toString)
			.orElse("");
	}
}
