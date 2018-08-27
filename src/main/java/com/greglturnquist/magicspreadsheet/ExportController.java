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

import static org.apache.poi.ss.usermodel.Row.*;
import static reactor.function.TupleUtils.*;

import lombok.extern.slf4j.Slf4j;

import java.io.FileOutputStream;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.sql.Date;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.Comparator;
import java.util.stream.IntStream;

import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.xssf.usermodel.XSSFSheet;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import reactor.core.publisher.Mono;
import reactor.core.scheduler.Schedulers;
import reactor.util.function.Tuple2;
import reactor.util.function.Tuples;
import org.springframework.core.io.AbstractResource;
import org.springframework.core.io.ClassPathResource;
import org.springframework.core.io.FileSystemResource;
import org.springframework.http.HttpHeaders;
import org.springframework.http.ResponseEntity;
import org.springframework.util.StringUtils;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * @author Greg Turnquist
 */
@RestController
@Slf4j
class ExportController {

	private final BookRepository bookRepository;
	private final AdTableRepository adTableRepository;
	private final AmsDataRepository amsDataRepository;
	private final EbookRoyaltyRepository royaltyRepository;
	private final KenpReadRepository kenpReadRepository;

	public static final DateTimeFormatter FORMATTER = DateTimeFormatter.ofPattern("yyyy-MM-dd");

	ExportController(BookRepository bookRepository,
					 AdTableRepository adTableRepository,
					 AmsDataRepository amsDataRepository,
					 EbookRoyaltyRepository royaltyRepository,
					 KenpReadRepository kenpReadRepository) {

		this.bookRepository = bookRepository;
		this.adTableRepository = adTableRepository;
		this.amsDataRepository = amsDataRepository;
		this.royaltyRepository = royaltyRepository;
		this.kenpReadRepository = kenpReadRepository;
	}

	@GetMapping(value = "/exportMagicSpreadsheet")
	ResponseEntity<Mono<AbstractResource>> exportMagicSpreadsheet() {

		String filename = "magic-spreadsheet-export-" + LocalDate.now() + "" + ".xlsm";

		return ResponseEntity.ok()
			.header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=\"" + filename + "\"")
			.body(populateTemplate(new ClassPathResource("sample-magic-spreadsheet.xlsm")));
	}

	private Mono<AbstractResource> populateTemplate(AbstractResource resource) {

		return Mono.just(resource)
			.flatMap(this::workbook)
			.flatMap(this::insertBooks)
			.flatMap(this::insertAds)
			.flatMap(this::insertAmsData)
			.flatMap(this::insertRoyaltyData)
			.flatMap(this::insertKenpReadData)
			.flatMap(this::writeToDisk)
			.subscribeOn(Schedulers.newSingle("exporter"));
	}

	private Mono<XSSFWorkbook> insertBooks(XSSFWorkbook workbook) {

		return worksheet(workbook, MagicSheets.BOOKS_SETUP.getSheetName())
			.flatMapMany(worksheet -> bookRepository.findAll()
				.sort(Comparator
					.comparing(Book::getSeries)
					.thenComparing(Book::getTitle))
				.flatMap(book -> Mono.just(worksheet).zipWith(Mono.just(book))))
			.index((index, objects) -> {

				XSSFSheet worksheet = objects.getT1();
				Book book = objects.getT2();

				Row row = worksheet.getRow(Math.toIntExact(index + 2));
				int rownum = row.getRowNum() + 1;

				row.getCell(MagicSpreadsheetBookSetupColumn.BookTitle.index(), CREATE_NULL_AS_BLANK).setCellValue(book.getCompleteTitle());
				row.getCell(MagicSpreadsheetBookSetupColumn.AuthorName.index(), CREATE_NULL_AS_BLANK).setCellValue(book.getAuthor());
				row.getCell(MagicSpreadsheetBookSetupColumn.BookShort.index(), CREATE_NULL_AS_BLANK).setCellValue(book.getBookShort());

				if (book.getSubTitle().isEmpty()) {
					row.getCell(MagicSpreadsheetBookSetupColumn.SeriesTitle.index(), CREATE_NULL_AS_BLANK).setCellValue((String) null);
				} else {
					row.getCell(MagicSpreadsheetBookSetupColumn.SeriesTitle.index(), CREATE_NULL_AS_BLANK).setCellValue(book.getSeries());
				}

				row.getCell(MagicSpreadsheetBookSetupColumn.AmazonASIN.index(), CREATE_NULL_AS_BLANK).setCellValue(book.getASIN());
				row.getCell(MagicSpreadsheetBookSetupColumn.KENPC.index(), CREATE_NULL_AS_BLANK).setCellValue(book.getKENPC());

				return Tuples.of(book, worksheet);
			})
			.map(objects -> Tuples.of(objects.getT1().getSeries(), objects.getT2()))
			.filter(objects -> StringUtils.hasText(objects.getT1()))
			.sort(Comparator.comparing(Tuple2::getT1))
			.distinct()
			.index((index, objects) -> {

				String series = objects.getT1();
				XSSFSheet worksheet = objects.getT2();

				Row row = worksheet.getRow(Math.toIntExact(index + 2));

				row.getCell(MagicSpreadsheetBookSetupColumn.SeriesName.index(), CREATE_NULL_AS_BLANK).setCellValue(series);

				return Mono.just(workbook);
			})
			.then(Mono.just(workbook));
	}

	private Mono<XSSFWorkbook> insertAds(XSSFWorkbook workbook) {

		return worksheet(workbook, MagicSheets.AD_TABLE.getSheetName())
			.flatMapMany(worksheet -> adTableRepository.findAll()
					.filter(adTableObject -> adTableObject.getBookTitle() != null)
					.sort(Comparator
						.comparing(AdTableObject::getStart)
						.thenComparing(AdTableObject::getCampaignName))
					.flatMap(adTableObject -> Mono.zip(
						Mono.just(worksheet),
						Mono.just(adTableObject),
						bookRepository.findByTitle(adTableObject.getBookTitle()))))
			.index((index, objects) -> {

				XSSFSheet worksheet = objects.getT1();
				AdTableObject ad = objects.getT2();
				Book book = objects.getT3();

				Row row = worksheet.getRow(Math.toIntExact(index + 1));
				int rownum = row.getRowNum() + 1;

				row.getCell(MagicSpreadsheetAdDataColumn.CampaignName.index(), CREATE_NULL_AS_BLANK).setCellValue(ad.getCampaignName());
				row.getCell(MagicSpreadsheetAdDataColumn.Type.index(), CREATE_NULL_AS_BLANK).setCellValue(ad.getType());
				row.getCell(MagicSpreadsheetAdDataColumn.Start.index(), CREATE_NULL_AS_BLANK).setCellValue(Date.valueOf(ad.getStart()));

				ad.getEnd().ifPresent(localDate -> {
					row.getCell(MagicSpreadsheetAdDataColumn.End.index(), CREATE_NULL_AS_BLANK).setCellValue(Date.valueOf(localDate));
				});

				row.getCell(MagicSpreadsheetAdDataColumn.Budget.index(), CREATE_NULL_AS_BLANK).setCellValue(ad.getBudget());
				row.getCell(MagicSpreadsheetAdDataColumn.BookTitle.index(), CREATE_NULL_AS_BLANK).setCellValue(book.getCompleteTitle());
				row.getCell(MagicSpreadsheetAdDataColumn.Series.index(), CREATE_NULL_AS_BLANK).setCellValue(ad.getSeries());

				return Mono.just(workbook);
			})
			.then(Mono.just(workbook));
	}

	private Mono<Boolean> exists(AmsDataObject amsDataObject) {

		return adTableRepository.findByCampaignName(amsDataObject.getCampaignName())
			.filter(adTableObject -> adTableObject.getBookTitle() != null)
			.flatMap(adTableObject -> bookRepository.findByTitle(adTableObject.getBookTitle()))
			.flatMap(book -> Mono.just(true))
			.switchIfEmpty(Mono.just(false));
	}

	private Mono<XSSFWorkbook> insertAmsData(XSSFWorkbook workbook) {

		return worksheet(workbook, MagicSheets.AMS_DATA.getSheetName())
			.flatMapMany(worksheet -> amsDataRepository.findAll()
				.filterWhen(this::exists)
				.sort(Comparator
					.comparing(AmsDataObject::getDate)
					.thenComparing(AmsDataObject::getStartDate)
					.thenComparing(AmsDataObject::getCampaignName))
				.flatMap(amsDataObject -> Mono.just(worksheet).zipWith(Mono.just(amsDataObject))))
			.index((index, objects) -> {

				XSSFSheet worksheet = objects.getT1();
				AmsDataObject amsDataObject = objects.getT2();

				Row row = worksheet.getRow(Math.toIntExact(index) + 1);
				int rownum = row.getRowNum() + 1;

				row.getCell(0, CREATE_NULL_AS_BLANK).setCellFormula("B" + rownum + "&T" + rownum);
				row.getCell(1, CREATE_NULL_AS_BLANK).setCellFormula("VLOOKUP(H" + rownum + ",AdLookupTable,6,FALSE)");
				row.getCell(2, CREATE_NULL_AS_BLANK).setCellFormula("VLOOKUP(B" + rownum + ",SeriesConvTable,4,FALSE)&E" + rownum);
				row.getCell(3, CREATE_NULL_AS_BLANK).setCellFormula("H" + rownum + "&T" + rownum);
				row.getCell(4, CREATE_NULL_AS_BLANK).setCellFormula("T" + rownum);
				row.getCell(5, CREATE_NULL_AS_BLANK).setCellFormula("IF(N" + rownum + ">1000,1,0)");

				row.getCell(MagicSpreadsheetAmsDataColumn.Status.index(), CREATE_NULL_AS_BLANK).setCellValue(amsDataObject.getStatus());
				row.getCell(MagicSpreadsheetAmsDataColumn.CampaignName.index(), CREATE_NULL_AS_BLANK).setCellValue(amsDataObject.getCampaignName());
				row.getCell(MagicSpreadsheetAmsDataColumn.Type.index(), CREATE_NULL_AS_BLANK).setCellValue(amsDataObject.getType());
				row.getCell(MagicSpreadsheetAmsDataColumn.StartDate.index(), CREATE_NULL_AS_BLANK).setCellValue(Date.valueOf(amsDataObject.getStartDate()));

				amsDataObject.getEndDate().ifPresent(localDate -> {
					row.getCell(MagicSpreadsheetAmsDataColumn.EndDate.index(), CREATE_NULL_AS_BLANK).setCellValue(Date.valueOf(localDate));
				});

				row.getCell(MagicSpreadsheetAmsDataColumn.Budget.index(), CREATE_NULL_AS_BLANK).setCellValue(amsDataObject.getBudget());
				row.getCell(MagicSpreadsheetAmsDataColumn.Spend.index(), CREATE_NULL_AS_BLANK).setCellValue(amsDataObject.getTotalSpend());
				row.getCell(MagicSpreadsheetAmsDataColumn.Impressions.index(), CREATE_NULL_AS_BLANK).setCellValue(amsDataObject.getImpressions().orElse(0.0));
				row.getCell(MagicSpreadsheetAmsDataColumn.Clicks.index(), CREATE_NULL_AS_BLANK).setCellValue(amsDataObject.getClicks().orElse(0.0));
				row.getCell(MagicSpreadsheetAmsDataColumn.AverageCpc.index(), CREATE_NULL_AS_BLANK).setCellValue(amsDataObject.getAverageCpc().orElse(0.0));
				row.getCell(MagicSpreadsheetAmsDataColumn.Date.index(), CREATE_NULL_AS_BLANK).setCellValue(Date.valueOf(amsDataObject.getDate()));

				return Mono.just(workbook);
			})
			.then(Mono.just(workbook));
	}

	private Mono<XSSFWorkbook> insertRoyaltyData(XSSFWorkbook workbook) {

		return Mono.zip(
			worksheet(workbook, MagicSheets.EBOOK_ROYALTY_DATA.getSheetName()),
			royaltyRepository.findAll()
				.sort(Comparator
					.comparing(EbookRoyaltyDataObject::getRoyaltyDate)
					.thenComparing(EbookRoyaltyDataObject::getTitle))
				.collectList())
			.flatMap(function((worksheet, royaltyDataObjects) -> {

				log.info("Ready to copy in the KDP Royalty table...");

				IntStream.range(0, royaltyDataObjects.size()).forEach(i -> {

					Row row = worksheet.getRow(i + 1);

					EbookRoyaltyDataObject royaltyDataObject = royaltyDataObjects.get(i);

					row.getCell(MagicSpreadsheetEbookRoyaltyDataColumn.RoyaltyDate.index(), CREATE_NULL_AS_BLANK).setCellValue(royaltyDataObject.getRoyaltyDate().format(FORMATTER));
					row.getCell(MagicSpreadsheetEbookRoyaltyDataColumn.Title.index(), CREATE_NULL_AS_BLANK).setCellValue(royaltyDataObject.getTitle());
					row.getCell(MagicSpreadsheetEbookRoyaltyDataColumn.AuthorName.index(), CREATE_NULL_AS_BLANK).setCellValue(royaltyDataObject.getAuthorName());
					row.getCell(MagicSpreadsheetEbookRoyaltyDataColumn.ASIN.index(), CREATE_NULL_AS_BLANK).setCellValue(royaltyDataObject.getASIN());
					row.getCell(MagicSpreadsheetEbookRoyaltyDataColumn.Marketplace.index(), CREATE_NULL_AS_BLANK).setCellValue(royaltyDataObject.getMarketplace());
					row.getCell(MagicSpreadsheetEbookRoyaltyDataColumn.RoyaltyType.index(), CREATE_NULL_AS_BLANK).setCellValue(royaltyDataObject.getRoyaltyType());
					row.getCell(MagicSpreadsheetEbookRoyaltyDataColumn.TransationType.index(), CREATE_NULL_AS_BLANK).setCellValue(royaltyDataObject.getTransactionType());
					row.getCell(MagicSpreadsheetEbookRoyaltyDataColumn.NetUnitsSold.index(), CREATE_NULL_AS_BLANK).setCellValue(royaltyDataObject.getNetUnitsSold());
					row.getCell(MagicSpreadsheetEbookRoyaltyDataColumn.Royalty.index(), CREATE_NULL_AS_BLANK).setCellValue(royaltyDataObject.getRoyalty());
					row.getCell(MagicSpreadsheetEbookRoyaltyDataColumn.Currency.index(), CREATE_NULL_AS_BLANK).setCellValue(royaltyDataObject.getCurrency());

				});

				return Mono.just(workbook);
			}));
	}


	private Mono<XSSFWorkbook> insertKenpReadData(XSSFWorkbook workbook) {

		return Mono.zip(
			worksheet(workbook, MagicSheets.KENP_READ_DATA.getSheetName()),
			kenpReadRepository.findAll()
				.sort(Comparator
					.comparing(KenpReadDataObject::getOrderDate)
					.thenComparing(KenpReadDataObject::getTitle))
				.collectList())
			.flatMap(function((worksheet, readDataObjects) -> {

				log.info("Ready to copy in the KENP Read table...");

				IntStream.range(0, readDataObjects.size()).forEach(i -> {

					Row row = worksheet.getRow(i + 1);

					KenpReadDataObject kenpReadDataObject = readDataObjects.get(i);

					row.getCell(MagicSpreadsheetKenpReadDataColumn.OrderDate.index(), CREATE_NULL_AS_BLANK).setCellValue(kenpReadDataObject.getOrderDate().format(FORMATTER));
					row.getCell(MagicSpreadsheetKenpReadDataColumn.Title.index(), CREATE_NULL_AS_BLANK).setCellValue(kenpReadDataObject.getTitle());
					row.getCell(MagicSpreadsheetKenpReadDataColumn.AuthorName.index(), CREATE_NULL_AS_BLANK).setCellValue(kenpReadDataObject.getAuthor());
					row.getCell(MagicSpreadsheetKenpReadDataColumn.ASIN.index(), CREATE_NULL_AS_BLANK).setCellValue(kenpReadDataObject.getASIN());
					row.getCell(MagicSpreadsheetKenpReadDataColumn.Marketplace.index(), CREATE_NULL_AS_BLANK).setCellValue(kenpReadDataObject.getMarketPlace());
					row.getCell(MagicSpreadsheetKenpReadDataColumn.PagesRead.index(), CREATE_NULL_AS_BLANK).setCellValue(kenpReadDataObject.getPagesRead());
				});

				return Mono.just(workbook);
			}));
	}


	private Mono<AbstractResource> writeToDisk(XSSFWorkbook workbook) {

		try {
			Path tempOutputFile = Files.createTempFile("magic-spreadsheet-", ".xlsm");

			Files.deleteIfExists(tempOutputFile);

			try (FileOutputStream fout = new FileOutputStream(tempOutputFile.toFile())) {
				log.info("Writing " + tempOutputFile.getFileName() + " to disk...");
				workbook.write(fout);
				log.info("Completed writing " + tempOutputFile.getFileName() + " to disk...");
			} catch (IOException e) {
				throw new RuntimeException(e);
			}

			return Mono.just(new FileSystemResource(tempOutputFile.toFile()));

		} catch (IOException e) {
			return Mono.empty();
		}
	}
	
	private Mono<FileSystemResource> copyToTempFile(AbstractResource resource) {

		try {
			Path tempFilePath = Files.createTempFile("magic-spreadsheet-", ".xlsm");
			
			Files.deleteIfExists(tempFilePath);
			Files.copy(resource.getInputStream(), tempFilePath);

			return Mono.just(new FileSystemResource(tempFilePath.toString()));
		} catch (IOException e) {
			throw new RuntimeException(e);
		}
	}

	private Mono<XSSFWorkbook> workbook(AbstractResource resource) {

		try {
			log.info("Opening workbook " + resource.getFile().getCanonicalPath());
			return Mono.just(new XSSFWorkbook(resource.getInputStream()));
		} catch (IOException e) {
			throw new RuntimeException(e);
		}
	}

	private Mono<XSSFSheet> worksheet(XSSFWorkbook workbook, String sheetName) {
		
		log.info("Opening worksheet " + sheetName);
		return Mono.just(workbook.getSheet(sheetName));
	}
}
