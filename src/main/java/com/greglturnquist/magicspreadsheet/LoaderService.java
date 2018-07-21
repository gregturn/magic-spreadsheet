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

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.io.Reader;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.sql.Date;
import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.time.Instant;
import java.util.Objects;
import java.util.Optional;
import java.util.function.Supplier;

import org.apache.commons.csv.CSVFormat;
import org.apache.commons.csv.CSVParser;
import org.apache.poi.ss.usermodel.Cell;
import org.apache.poi.ss.usermodel.Workbook;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;
import org.springframework.core.io.FileSystemResource;
import org.springframework.data.mongodb.core.MongoOperations;
import org.springframework.http.codec.multipart.FilePart;
import org.springframework.stereotype.Service;

/**
 * @author Greg Turnquist
 */
@Service
@Slf4j
class LoaderService {

	private static String UPLOAD_ROOT = "upload-dir";
	private static SimpleDateFormat DATE_FORMAT = new SimpleDateFormat("yyyy/MM/dd");

	private final MongoOperations operations;
	private final AmsDataRepository repository;

	LoaderService(MongoOperations operations, AmsDataRepository repository) {

		this.operations = operations;
		this.repository = repository;
	}

	Mono<Void> importMagicSpreadsheet(FilePart excelWorkbook) {

		return uploadFile(excelWorkbook, UPLOAD_ROOT)
			.then(toInputStream(excelWorkbook, UPLOAD_ROOT))
			.flatMap(this::loadMagicSpreadsheet)
			.then();

		// TODO Delete file afterward
	}

	Mono<Void> importAmsReport(FilePart csvFilePart) {

		return uploadFile(csvFilePart, UPLOAD_ROOT)
			.then(toReader(csvFilePart.filename(), UPLOAD_ROOT))
			.flatMap(reader -> loadAmsReport(reader, Date.from(Instant.now())))
			.then();
	}

	/**
	 * Wipe out existing data and reload new data.
	 * 
	 * @param inputStream
	 * @throws IOException
	 */
	Mono<Void> loadMagicSpreadsheet(InputStream inputStream) {

		Workbook workbook = null;
		try {
			workbook = new XSSFWorkbook(inputStream);
		} catch (IOException e) {
			throw new RuntimeException(e);
		}

		log.info("Dropping AMS Data...");

		operations.dropCollection(AmsDataObject.class);

		log.info("Loading AMS Data...");

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
				} catch (IllegalStateException|NullPointerException e) {
					log.error("Failed to parse " + AMS_DATA.name() + ": rowNum=" + row.getRowNum() + " " + e.getMessage());
					return null;
				}
			})
			.filter(Objects::nonNull)
			.forEach(operations::insert);

		log.info("Dropping Ad Table Data...");

		operations.dropCollection(AdTableObject.class);

		log.info("Loading Ad Table Data...");

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

		log.info("Dropping eBook Royalty data...");

		operations.dropCollection(EbookRoyaltyDataObject.class);

		log.info("Loading eBook Royalty data...");

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
			.filter(ebookRoyaltyDataObject -> !ebookRoyaltyDataObject.getTransactionType().contains("Free"))
			.forEach(operations::insert);

		log.info("Dropping Book Setup Data...");

		operations.dropCollection(Book.class);

		log.info("Loading Book Setup Data...");

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

		log.info("Dropping KENP Read data...");

		operations.dropCollection(KenpReadDataObject.class);

		log.info("Loading KENP Read data...");

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

		return Mono.empty();
	}

	Mono<Void> loadAmsReport(Reader reader, java.util.Date date) {

		try {
			CSVParser parser = new CSVParser(reader, CSVFormat.DEFAULT
				.withFirstRecordAsHeader()
				.withIgnoreHeaderCase()
				.withTrim());

			log.info("Loading AMS report for " + date.toString() + "!!!");

			return Flux.fromIterable(parser.getRecords())
				.log("importAms-flatmapCsv")
				.flatMap(csvRecord -> {
					try {
						return Mono.just(new AmsDataObject(
							null,
							toInt(csvRecord.getRecordNumber()),
							csvRecord.get("\uFEFFStatus"),
							csvRecord.get("Campaign Name"),
							csvRecord.get("Type"),
							DATE_FORMAT.parse(csvRecord.get("Start Date")),
							toOptionalDate(csvRecord.get("End Date")),
							toDouble(csvRecord.get("Budget")),
							toDouble(csvRecord.get("Spend")),
							toOptionalDouble(csvRecord.get("Impressions")),
							toOptionalDouble(csvRecord.get("Clicks")),
							toOptionalDouble(csvRecord.get("Average CPC")),
							date));
					} catch (ParseException e) {
						log.error("Unable to parse #" + csvRecord.getRecordNumber() + " " + csvRecord.toString() + " => " + e.getMessage());
						return Mono.empty();
					}
				})
				.log("importAms-zipWithLatestAmsRecord")
				.flatMap(amsDataObject -> Mono.zip(
					repository.findFirstByCampaignNameOrderByDateDesc(amsDataObject.getCampaignName()),
					Mono.just(amsDataObject)))
				.log("importAms-create-newAmsRecord")
				.map(objects -> {
					log.info("Most recent => " + objects.getT1().toString());
					log.info("New record => " + objects.getT2().toString());
					return new AmsDataObject(
						null,
						-1,
						objects.getT2().getStatus(),
						objects.getT2().getCampaignName(),
						objects.getT2().getType(),
						objects.getT2().getStartDate(),
						objects.getT2().getEndDate(),
						objects.getT2().getBudget(),
						objects.getT2().getSpend(),
						objects.getT2().getImpressions()
							.map(aDouble -> Optional.of(aDouble - objects.getT1().getImpressions().orElse(0.0)))
							.orElse(objects.getT1().getImpressions()),
						objects.getT2().getClicks()
							.map(aDouble -> Optional.of(aDouble - objects.getT1().getClicks().orElse(0.0)))
							.orElse(objects.getT1().getClicks()),
						objects.getT2().getAverageCpc(),
						date);
				})
				.log("importAms-logitall")
				.map(amsDataObject -> {
					log.info("Diff'd AMS Data => " + amsDataObject.toString());
					return amsDataObject;
				})
				.log("importAms-saveToMongoDB")
				.flatMap(repository::save)
				.log("importAms-closeParser")
				.then(Mono.fromRunnable(() -> {
					try {
						parser.close();
					} catch (IOException ignored) {
					}
				}));
		} catch (IOException e) {
			throw new RuntimeException(e);
		}
	}

	private int toInt(long value) {
		return new Long(value).intValue();
	}

	private double toDouble(String value) {

		try {
			return Double.parseDouble(value.replaceAll(",", ""));
		} catch (NumberFormatException e) {
			log.error("Failed to parse '" + value + "' => " + e.getMessage());
			return 0.0;
		}
	}
	private Optional<Double> toOptionalDouble(String value) {

		try {
			return Optional.of(Double.parseDouble(value.replaceAll(",", "")));
		} catch (NumberFormatException e) {
			log.error("Failed to parse '" + value + "' => " + e.getMessage());
			return Optional.empty();
		}
	}

	private Optional<java.util.Date> toOptionalDate(String value) {

		try {
			return Optional.of(DATE_FORMAT.parse(value));
		} catch (ParseException e) {
			return Optional.empty();
		}
	}

	private static Mono<Void> uploadFile(FilePart file, String rootDir) {

		return toFile(file, rootDir)
			.log("uploadFile-fullPath")
			.map(destFile -> {
				try {
					destFile.createNewFile();
					return destFile;
				} catch (IOException e) {
					throw new RuntimeException(e);
				}
			})
			.log("uploadFile-newFile")
			.flatMap(file::transferTo)
			.log("uploadFile-transferTo");
	}

	private static Mono<File> toFile(FilePart file, String rootDir) {
		return Mono.just(Paths.get(rootDir, file.filename()).toFile());
	}

	/**
	 * If the rule checks out, return the original argument. Otherwise return a Mono.empty().
	 *
	 * @param item
	 * @param validationRule
	 * @param <T>
	 * @return
	 */
	private <T> Mono<T> validate(T item, Supplier<Boolean> validationRule) {
		return validationRule.get() ? Mono.just(item) : Mono.empty();
	}
	
	private static Mono<InputStream> toInputStream(FilePart file, String rootDir) {

		return toFile(file, rootDir)
			.map(FileSystemResource::new)
			.map(fileSystemResource -> {
				try {
					return fileSystemResource.getInputStream();
				} catch (IOException e) {
					throw new RuntimeException(e);
				}
			});
	}

	static Reader toReader(String filename) {
		return toReader(filename, "").block();
	}

	private static Mono<Reader> toReader(String filename, String rootDir) {

		return Mono.defer(() -> {
			try {
				return Mono.just(Files.newBufferedReader(Paths.get(rootDir, filename)));
			} catch (IOException e) {
				throw new RuntimeException(e);
			}
		});
	}
}
