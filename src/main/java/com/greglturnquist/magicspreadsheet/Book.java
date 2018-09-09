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

import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.mapping.Document;

/**
 * @author Greg Turnquist
 */
@Data
@AllArgsConstructor
@Document
class Book {

	@Id String id;
	int rowNum;
	long number;
	String title;
	String subTitle;
	String author;
	String bookShort;
	String series;
	Integer seriesNumber;
	String ASIN;
	double KENPC;

	static final Book NONE = new Book(
		"",
		-1,
		-1,
		"",
		"",
		"",
		"",
		"",
		null,
		"",
		0.1
	);

	String getCompleteTitle() {

		if (this.subTitle.isEmpty()) {
			return this.title;
		}

		return this.title + ": " + this.subTitle;
	}
}
