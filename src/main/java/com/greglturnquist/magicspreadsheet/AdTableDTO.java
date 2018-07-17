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

import java.util.Date;

/**
 * @author Greg Turnquist
 */
@RequiredArgsConstructor
class AdTableDTO {

	private final AdTableObject object;

	public int getRowNum() {
		return this.object.getRowNum();
	}

	public String getCampaignName() {
		return this.object.getCampaignName();
	}

	public String getType() {
		return this.object.getType();
	}

	public String getStart() {
		return this.object.getStart().toString();
	}

	public String getEnd() {
		return this.object.getEnd()
			.map(Date::toString)
			.orElse("");
	}

	public double getBudget() {
		return this.object.getBudget();
	}

	public String getBookTitle() {
		return this.object.getBookTitle();
	}

	public String getSeries() {
		return this.object.getSeries();
	}
}
