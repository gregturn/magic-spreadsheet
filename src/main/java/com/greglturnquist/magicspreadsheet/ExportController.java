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

import static org.apache.poi.ss.usermodel.Row.CREATE_NULL_AS_BLANK;
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
import java.util.Optional;
import java.util.stream.IntStream;

import org.apache.poi.ss.usermodel.Cell;
import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.xssf.usermodel.XSSFSheet;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;
import reactor.core.scheduler.Schedulers;
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

		return Mono.zip(
				worksheet(workbook, MagicSheets.BOOKS_SETUP.getSheetName()),
				bookRepository.findAll()
					.sort(Comparator
						.comparing(Book::getSeries)
						.thenComparing(Book::getTitle))
					.collectList())
			.flatMap(function((worksheet, books) -> {

				log.info("Ready to copy in the BOOK table...");

				IntStream.range(0, books.size()).forEach(i -> {

					Row row = worksheet.getRow(i + 2);

					Optional.ofNullable(row.getCell(MagicSpreadsheetBookSetupColumn.BookTitle.index()))
						.ifPresent(cell -> {
							cell.setCellValue(books.get(i).getTitle());
						});

					Optional.ofNullable(row.getCell(MagicSpreadsheetBookSetupColumn.AuthorName.index()))
						.ifPresent(cell -> {
							cell.setCellValue(books.get(i).getAuthor());
						});

					Optional.ofNullable(row.getCell(MagicSpreadsheetBookSetupColumn.BookShort.index()))
						.ifPresent(cell -> {
							cell.setCellValue(books.get(i).getBookShort());
						});

					Optional.ofNullable(row.getCell(MagicSpreadsheetBookSetupColumn.SeriesTitle.index()))
						.ifPresent(cell -> {
							if (books.get(i).getSeries().isEmpty()) {
								cell.setCellValue((String) null);
							} else {
								cell.setCellValue(books.get(i).getSeries());
							}
						});

					Optional.ofNullable(row.getCell(MagicSpreadsheetBookSetupColumn.AmazonASIN.index()))
						.ifPresent(cell -> {
							cell.setCellValue(books.get(i).getASIN());
						});

					Optional.ofNullable(row.getCell(MagicSpreadsheetBookSetupColumn.KENPC.index()))
						.ifPresent(cell -> {
							cell.setCellValue(books.get(i).getKENPC());
						});
				});

				return Flux.fromIterable(books)
					.map(Book::getSeries)
					.filter(StringUtils::hasText)
					.sort(String::compareTo)
					.distinct()
					.collectList()
					.zipWith(Mono.just(worksheet));
			}))
			.flatMap(function((seriesList, worksheet) -> {

				IntStream.range(0, seriesList.size()).forEach(i -> {

					Row row = worksheet.getRow(i + 2);

					Cell cell = row.getCell(MagicSpreadsheetBookSetupColumn.SeriesName.index(), CREATE_NULL_AS_BLANK);
					cell.setCellValue(seriesList.get(i));
				});

				return Mono.just(workbook);
			}));
	}

	private Mono<XSSFWorkbook> insertAds(XSSFWorkbook workbook) {

		return Mono.zip(
				worksheet(workbook, MagicSheets.AD_TABLE.getSheetName()),
				adTableRepository.findAll()
					.filter(adTableObject -> adTableObject.getBookTitle() != null)
					.sort(Comparator
						.comparing(AdTableObject::getStart)
						.thenComparing(AdTableObject::getCampaignName))
					.collectList())
			.flatMap(function((worksheet, ads) -> {

				log.info("Ready to copy in the ADS table...");
				IntStream.range(0, ads.size()).forEach(i -> {

					Row row = worksheet.getRow(i + 1);

					Optional.ofNullable(row.getCell(MagicSpreadsheetAdDataColumn.CampaignName.index()))
						.ifPresent(cell -> {
							cell.setCellValue(ads.get(i).getCampaignName());
						});

					Optional.ofNullable(row.getCell(MagicSpreadsheetAdDataColumn.Type.index()))
						.ifPresent(cell -> {
							cell.setCellValue(ads.get(i).getType());
						});

					Optional.ofNullable(row.getCell(MagicSpreadsheetAdDataColumn.Start.index()))
						.ifPresent(cell -> {
							cell.setCellValue(Date.valueOf(ads.get(i).getStart()));
						});

					Optional.ofNullable(row.getCell(MagicSpreadsheetAdDataColumn.End.index()))
						.ifPresent(cell -> {
							ads.get(i).getEnd().ifPresent(endDate -> {
								cell.setCellValue(Date.valueOf(endDate));
							});
						});

					Optional.ofNullable(row.getCell(MagicSpreadsheetAdDataColumn.Budget.index()))
						.ifPresent(cell -> {
							cell.setCellValue(ads.get(i).getBudget());
						});

					Optional.ofNullable(row.getCell(MagicSpreadsheetAdDataColumn.BookTitle.index()))
						.ifPresent(cell -> {
							cell.setCellValue(ads.get(i).getBookTitle());
						});

					Optional.ofNullable(row.getCell(MagicSpreadsheetAdDataColumn.Series.index()))
						.ifPresent(cell -> {
							cell.setCellValue(ads.get(i).getSeries());
						});
				});

				return Mono.just(workbook);
			}));
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

//		return Mono.zip(
//			worksheet(workbook, MagicSheets.AMS_DATA.getSheetName()),
//			amsDataRepository.findAll()
//				.filterWhen(this::exists)
//				.sort(Comparator
//					.comparing(AmsDataObject::getDate)
//					.thenComparing(AmsDataObject::getStartDate)
//					.thenComparing(AmsDataObject::getCampaignName))
//				.collectList())
//			.flatMap(function((worksheet, amsDataObjects) -> {
//
//				log.info("Ready to copy in the AMS Data table...");
//
//				IntStream.range(1, amsDataObjects.size()).forEach(i -> {
//
//					Row row = worksheet.getRow(i);
//					int rownum = row.getRowNum() + 1;
//
//					row.getCell(0, CREATE_NULL_AS_BLANK).setCellFormula("B" + rownum + "&T" + rownum);
//					row.getCell(1, CREATE_NULL_AS_BLANK).setCellFormula("VLOOKUP(H" + rownum + ",AdLookupTable,6,FALSE)");
//					row.getCell(2, CREATE_NULL_AS_BLANK).setCellFormula("VLOOKUP(B" + rownum + ",SeriesConvTable,4,FALSE)&E" + rownum);
//					row.getCell(3, CREATE_NULL_AS_BLANK).setCellFormula("H" + rownum + "&T" + rownum);
//					row.getCell(4, CREATE_NULL_AS_BLANK).setCellFormula("T" + rownum);
//					row.getCell(5, CREATE_NULL_AS_BLANK).setCellFormula("IF(N" + rownum + ">1000,1,0)");
//
//					row.getCell(MagicSpreadsheetAmsDataColumn.Status.index(), CREATE_NULL_AS_BLANK).setCellValue(amsDataObjects.get(i).getStatus());
//					row.getCell(MagicSpreadsheetAmsDataColumn.CampaignName.index(), CREATE_NULL_AS_BLANK).setCellValue(amsDataObjects.get(i).getCampaignName());
//					row.getCell(MagicSpreadsheetAmsDataColumn.Type.index(), CREATE_NULL_AS_BLANK).setCellValue(amsDataObjects.get(i).getType());
//					row.getCell(MagicSpreadsheetAmsDataColumn.StartDate.index(), CREATE_NULL_AS_BLANK).setCellValue(Date.valueOf(amsDataObjects.get(i).getStartDate()));
//
//					amsDataObjects.get(i).getEndDate().ifPresent(localDate -> {
//						row.getCell(MagicSpreadsheetAmsDataColumn.EndDate.index(), CREATE_NULL_AS_BLANK).setCellValue(Date.valueOf(localDate));
//					});
//
//					row.getCell(MagicSpreadsheetAmsDataColumn.Budget.index(), CREATE_NULL_AS_BLANK).setCellValue(amsDataObjects.get(i).getBudget());
//					row.getCell(MagicSpreadsheetAmsDataColumn.Spend.index(), CREATE_NULL_AS_BLANK).setCellValue(amsDataObjects.get(i).getTotalSpend());
//					row.getCell(MagicSpreadsheetAmsDataColumn.Impressions.index(), CREATE_NULL_AS_BLANK).setCellValue(amsDataObjects.get(i).getImpressions().orElse(0.0));
//					row.getCell(MagicSpreadsheetAmsDataColumn.Clicks.index(), CREATE_NULL_AS_BLANK).setCellValue(amsDataObjects.get(i).getClicks().orElse(0.0));
//					row.getCell(MagicSpreadsheetAmsDataColumn.AverageCpc.index(), CREATE_NULL_AS_BLANK).setCellValue(amsDataObjects.get(i).getAverageCpc().orElse(0.0));
//					row.getCell(MagicSpreadsheetAmsDataColumn.Date.index(), CREATE_NULL_AS_BLANK).setCellValue(Date.valueOf(amsDataObjects.get(i).getDate()));
//				});
//
//				return Mono.just(workbook);
//			}));
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

					Optional.ofNullable(row.getCell(MagicSpreadsheetEbookRoyaltyDataColumn.RoyaltyDate.index()))
						.ifPresent(cell -> {
							cell.setCellValue(royaltyDataObjects.get(i).getRoyaltyDate().format(FORMATTER));
						});

					Optional.ofNullable(row.getCell(MagicSpreadsheetEbookRoyaltyDataColumn.Title.index()))
						.ifPresent(cell -> {
							cell.setCellValue(royaltyDataObjects.get(i).getTitle());
						});

					Optional.ofNullable(row.getCell(MagicSpreadsheetEbookRoyaltyDataColumn.AuthorName.index()))
						.ifPresent(cell -> {
							cell.setCellValue(royaltyDataObjects.get(i).getAuthorName());
						});

					Optional.ofNullable(row.getCell(MagicSpreadsheetEbookRoyaltyDataColumn.ASIN.index()))
						.ifPresent(cell -> {
							cell.setCellValue(royaltyDataObjects.get(i).getASIN());
						});

					Optional.ofNullable(row.getCell(MagicSpreadsheetEbookRoyaltyDataColumn.Marketplace.index()))
						.ifPresent(cell -> {
							cell.setCellValue(royaltyDataObjects.get(i).getMarketplace());
						});

					Optional.ofNullable(row.getCell(MagicSpreadsheetEbookRoyaltyDataColumn.RoyaltyType.index()))
						.ifPresent(cell -> {
							cell.setCellValue(royaltyDataObjects.get(i).getRoyaltyType());
						});

					Optional.ofNullable(row.getCell(MagicSpreadsheetEbookRoyaltyDataColumn.TransationType.index()))
						.ifPresent(cell -> {
							cell.setCellValue(royaltyDataObjects.get(i).getTransactionType());
						});

					Optional.ofNullable(row.getCell(MagicSpreadsheetEbookRoyaltyDataColumn.NetUnitsSold.index()))
						.ifPresent(cell -> {
							cell.setCellValue(royaltyDataObjects.get(i).getNetUnitsSold());
						});

					Optional.ofNullable(row.getCell(MagicSpreadsheetEbookRoyaltyDataColumn.Royalty.index()))
						.ifPresent(cell -> {
							cell.setCellValue(royaltyDataObjects.get(i).getRoyalty());
						});

					Optional.ofNullable(row.getCell(MagicSpreadsheetEbookRoyaltyDataColumn.Currency.index()))
						.ifPresent(cell -> {
							cell.setCellValue(royaltyDataObjects.get(i).getCurrency());
						});

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

					Optional.ofNullable(row.getCell(MagicSpreadsheetKenpReadDataColumn.OrderDate.index()))
						.ifPresent(cell -> {
							cell.setCellValue(readDataObjects.get(i).getOrderDate().format(FORMATTER));
						});

					Optional.ofNullable(row.getCell(MagicSpreadsheetKenpReadDataColumn.Title.index()))
						.ifPresent(cell -> {
							cell.setCellValue(readDataObjects.get(i).getTitle());
						});

					Optional.ofNullable(row.getCell(MagicSpreadsheetKenpReadDataColumn.AuthorName.index()))
						.ifPresent(cell -> {
							cell.setCellValue(readDataObjects.get(i).getAuthor());
						});

					Optional.ofNullable(row.getCell(MagicSpreadsheetKenpReadDataColumn.ASIN.index()))
						.ifPresent(cell -> {
							cell.setCellValue(readDataObjects.get(i).getASIN());
						});

					Optional.ofNullable(row.getCell(MagicSpreadsheetKenpReadDataColumn.Marketplace.index()))
						.ifPresent(cell -> {
							cell.setCellValue(readDataObjects.get(i).getMarketPlace());
						});

					Optional.ofNullable(row.getCell(MagicSpreadsheetKenpReadDataColumn.PagesRead.index()))
						.ifPresent(cell -> {
							cell.setCellValue(readDataObjects.get(i).getPagesRead());
						});
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
