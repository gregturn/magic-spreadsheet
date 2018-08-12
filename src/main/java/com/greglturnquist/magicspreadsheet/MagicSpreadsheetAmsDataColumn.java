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
import java.util.Optional;

import org.apache.poi.ss.usermodel.Cell;
import org.apache.poi.ss.usermodel.Row;

/**
 * @author Greg Turnquist
 */
@Slf4j
enum MagicSpreadsheetAmsDataColumn {

	Status(6),
	CampaignName(7),
	Type(8),
	StartDate(9),
	EndDate(10),
	Budget(11),
	Spend(12),
	Impressions(13),
	Clicks(14),
	AverageCpc(15),
	DetailedPageView(16),
	TotalSales(17),
	ACoS(18),
	Date(19);

	private final int index;

	MagicSpreadsheetAmsDataColumn(int index) {
		this.index = index;
	}

	public int index() {
		return index;
	}

	public Cell getValue(Row row) {
		return row.getCell(this.index);
	}

	public int cellType(Row row) {
		return row.getCell(this.index).getCellType();
	}

	public double numericValue(Row row) {
		return row.getCell(this.index).getNumericCellValue();
	}

	public String stringValue(Row row) {
		return row.getCell(this.index).getStringCellValue();
	}

	public LocalDate dateValue(Row row) {
		return row.getCell(this.index).getDateCellValue().toInstant().atZone(ZoneId.systemDefault()).toLocalDate();
	}

	public Optional<LocalDate> optionalDateValue(Row row) {
		return this.cellType(row) == Cell.CELL_TYPE_NUMERIC ? Optional.of(this.dateValue(row)) : Optional.empty();
	}

	public Optional<Double> optionalNumericValue(Row row) {
		return this.cellType(row) == Cell.CELL_TYPE_NUMERIC ? Optional.of(this.numericValue(row)) : Optional.empty();
	}
}
