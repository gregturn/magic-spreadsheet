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

	public String getImpressions() {
		return this.object.getImpressions()
			.map(Object::toString)
			.orElse("");
	}

	public String getClicks() {
		return this.object.getClicks()
			.map(Object::toString)
			.orElse("");
	}

	public String getAverageCpc() {
		return this.object.getAverageCpc()
			.map(Object::toString)
			.orElse("");
	}

	public String getSpend() {
		return this.object.getAverageCpc()
			.map(averageCpc -> "$" + averageCpc * this.object.getClicks().orElse(0.0))
			.orElse("");
	}
}
