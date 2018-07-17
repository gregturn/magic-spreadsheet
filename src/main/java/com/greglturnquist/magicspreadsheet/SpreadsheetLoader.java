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

import static com.greglturnquist.magicspreadsheet.MagicSheets.*;

import lombok.extern.slf4j.Slf4j;

import java.sql.Date;
import java.util.Objects;

import org.apache.poi.ss.usermodel.Cell;
import org.apache.poi.ss.usermodel.Workbook;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.springframework.boot.CommandLineRunner;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.io.ClassPathResource;
import org.springframework.data.mongodb.core.MongoOperations;

/**
 * @author Greg Turnquist
 */
@Configuration
@Slf4j
class SpreadsheetLoader {

	@Bean
	CommandLineRunner load(MongoOperations operations, AmsDataRepository repository) {
		return args -> {
			ClassPathResource magicSpreadsheet = new ClassPathResource("sample-magic-spreadsheet.xlsm");
			Workbook workbook = new XSSFWorkbook(magicSpreadsheet.getInputStream());

			operations.dropCollection(AmsDataObject.class);

			AMS_DATA.stream(workbook)
				.filter(row -> row.getCell(AmsDataColumn.Status.index()) != null)
				.map(row -> {
					try {
						return new AmsDataObject(
							null,
							row.getRowNum(),
							AmsDataColumn.Status.stringValue(row),
							AmsDataColumn.CampaignName.stringValue(row),
							AmsDataColumn.Type.stringValue(row),
							AmsDataColumn.StartDate.dateValue(row),
							AmsDataColumn.EndDate.optionalDateValue(row),
							AmsDataColumn.Budget.numericValue(row),
							AmsDataColumn.Spend.numericValue(row),
							AmsDataColumn.Impressions.optionalNumericValue(row),
							AmsDataColumn.Clicks.optionalNumericValue(row),
							AmsDataColumn.AverageCpc.optionalNumericValue(row),
							AmsDataColumn.Date.dateValue(row));
					} catch (IllegalStateException e) {
						log.error("Failed to parse " + AMS_DATA.name() + ": rowNum=" + row.getRowNum());
						return null;
					}
				})
				.filter(Objects::nonNull)
				.forEach(operations::insert);

			operations.dropCollection(AdTableObject.class);

			AD_TABLE.stream(workbook)
				.filter(row -> !AdDataColumn.CampaignName.stringValue(row).equals(""))
				.map(row -> {
					try {
						return new AdTableObject(
							null,
							row.getRowNum(),
							AdDataColumn.CampaignName.stringValue(row),
							AdDataColumn.Type.stringValue(row),
							AdDataColumn.Start.dateValue(row),
							AdDataColumn.End.optionalDateValue(row),
							AdDataColumn.Budget.numericValue(row),
							AdDataColumn.BookTitle.stringValue(row),
							AdDataColumn.Series.stringValue(row));
					} catch (IllegalStateException e) {
						log.error("Failed to parse " + AD_TABLE.name() + ": rowNum=" + row.getRowNum());
						return null;
					}
				})
				.filter(Objects::nonNull)
				.forEach(operations::insert);

			operations.dropCollection(EbookRoyaltyDataObject.class);

			EBOOK_ROYALTY_DATA.stream(workbook)
				.filter(row -> row.getCell(EbookRoyaltyDataColumn.Title.index()) != null && EbookRoyaltyDataColumn.Title.cellType(row) != Cell.CELL_TYPE_BLANK)
				.map(row -> {
					try {
						return new EbookRoyaltyDataObject(
							null,
							row.getRowNum(),
							Date.valueOf(EbookRoyaltyDataColumn.RoyaltyDate.stringValue(row)),
							EbookRoyaltyDataColumn.Title.stringValue(row),
							EbookRoyaltyDataColumn.AuthorName.stringValue(row),
							EbookRoyaltyDataColumn.ASIN.stringValue(row),
							EbookRoyaltyDataColumn.Marketplace.stringValue(row),
							EbookRoyaltyDataColumn.RoyaltyType.stringValue(row),
							EbookRoyaltyDataColumn.TransationType.stringValue(row),
							EbookRoyaltyDataColumn.NetUnitsSold.cellType(row) == Cell.CELL_TYPE_NUMERIC ? EbookRoyaltyDataColumn.NetUnitsSold.numericValue(row) : Double.parseDouble(EbookRoyaltyDataColumn.NetUnitsSold.stringValue(row)),
							EbookRoyaltyDataColumn.Royalty.cellType(row) == Cell.CELL_TYPE_NUMERIC ? EbookRoyaltyDataColumn.Royalty.numericValue(row) : Double.parseDouble(EbookRoyaltyDataColumn.Royalty.stringValue(row)),
							EbookRoyaltyDataColumn.Currency.stringValue(row));
					} catch (IllegalStateException|IllegalArgumentException e) {
						log.error("Failed to parse " + EBOOK_ROYALTY_DATA.name() + ": rowNum=" + row.getRowNum());
						return null;
					}
				})
				.filter(Objects::nonNull)
				.forEach(operations::insert);

			operations.dropCollection(Book.class);

			BOOKS_SETUP.stream(workbook)
				.filter(row -> row.getCell(BookSetupColumn.Counter.index()) != null && !BookSetupColumn.BookTitle.stringValue(row).equals(""))
				.map(row -> {
					try {
						return new Book(
							null,
							row.getRowNum(),
							new Double(BookSetupColumn.Counter.numericValue(row)).longValue(),
							BookSetupColumn.BookTitle.stringValue(row),
							BookSetupColumn.AuthorName.stringValue(row),
							BookSetupColumn.BookShort.stringValue(row),
							BookSetupColumn.SeriesTitle.stringValue(row),
							BookSetupColumn.AmazonASIN.stringValue(row),
							BookSetupColumn.KENPC.numericValue(row));
					} catch (IllegalStateException|IllegalArgumentException e) {
						log.error("Failed to parse " + BOOKS_SETUP.name() + ": rowNum=" + row.getRowNum());
						return null;
					}
				})
				.filter(Objects::nonNull)
				.forEach(operations::insert);

			operations.dropCollection(KenpReadDataObject.class);

			KENP_READ_DATA.stream(workbook)
				.filter(row -> row.getCell(KenpReadDataColumn.Title.index()) != null && !KenpReadDataColumn.Title.stringValue(row).equals(""))
				.map(row -> {
					try {
						return new KenpReadDataObject(
							null,
							row.getRowNum(),
							Date.valueOf(KenpReadDataColumn.OrderDate.stringValue(row)),
							KenpReadDataColumn.Title.stringValue(row),
							KenpReadDataColumn.AuthorName.stringValue(row),
							KenpReadDataColumn.ASIN.stringValue(row),
							KenpReadDataColumn.Marketplace.stringValue(row),
							KenpReadDataColumn.PagesRead.numericValue(row));
					} catch (IllegalStateException|IllegalArgumentException e) {
						log.error("Failed to parse " + KENP_READ_DATA.name() + ": rowNum=" + row.getRowNum());
						return null;
					}
				})
				.filter(Objects::nonNull)
				.forEach(operations::insert);
		};
	}

}
