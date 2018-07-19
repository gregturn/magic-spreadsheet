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
import lombok.Value;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonPropertyOrder;
import com.fasterxml.jackson.annotation.JsonUnwrapped;

/**
 * @author Greg Turnquist
 */
@Value
@AllArgsConstructor
@JsonPropertyOrder({"title", "adPerformanceStats", "totalPageReads", "unitsSold",
	"unitsSoldViaPageReads", "unitsSoldTotal", "clickThroughRate", "conversionRate", "totalAdSpend", "totalEarnings", "ROI"})
class BookDTO {

	String title;
	@JsonUnwrapped AdPerformanceStats adPerformanceStats;
	double unitsSold;
	@JsonIgnore double totalPageReads;
	@JsonIgnore double kenpc;
	double totalAdSpend;
	double totalEarnings;

	public String getConversionRate() {
		if (this.adPerformanceStats.getClicks() == 0.0) {
			return "No clicks were used in the selling of this product";
		} else {
			return "1:" + this.adPerformanceStats.getClicks() / getUnitsSoldTotal();
		}
	}

	public double getUnitsSoldViaPageReads() {

		if (this.kenpc >= 1.0) {
			return this.totalPageReads / this.kenpc;
		} else {
			return 0.0;
		}
	}

	public double getUnitsSoldTotal() {
		return this.unitsSold + getUnitsSoldViaPageReads();
	}

	public String getClickThroughRate() {
		
		if (this.adPerformanceStats.getClicks() == 0.0) {
			return "No clicks were used in the selling of this product";
		}
		return "1 click every " + this.adPerformanceStats.getImpressions() / this.adPerformanceStats.getClicks() + " impressions";
	}

	public String getTotalAdSpend() {
		return "$" + this.totalAdSpend;
	}

	public String getTotalEarnings() {
		return "$" + this.totalEarnings;
	}

	public String getROI() {
		return (this.totalEarnings - this.totalAdSpend) * 100.0 / this.totalAdSpend + "%";
	}
}
