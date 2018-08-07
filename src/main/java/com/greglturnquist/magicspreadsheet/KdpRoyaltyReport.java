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

import java.util.stream.Stream;
import java.util.stream.StreamSupport;

import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.ss.usermodel.Sheet;
import org.apache.poi.ss.usermodel.Workbook;

/**
 * @author Greg Turnquist
 */
enum KdpRoyaltyReport {

	EBOOK_ROYALTY("eBook Royalty"),
	KENP_READ("KENP Read");

	private final String sheetName;

	KdpRoyaltyReport(String sheetName) {
		this.sheetName = sheetName;
	}

	Sheet getSheet(Workbook workbook) {
		return workbook.getSheet(this.sheetName);
	}

	Stream<Row> stream(Workbook workbook) {
		return StreamSupport.stream(getSheet(workbook).spliterator(), false);
	}

}
