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

import static com.greglturnquist.magicspreadsheet.KdpRoyaltyReport.*;
import static com.greglturnquist.magicspreadsheet.MagicSheets.*;
import static org.springframework.data.mongodb.core.query.Criteria.*;
import static org.springframework.data.mongodb.core.query.Query.*;

import lombok.extern.slf4j.Slf4j;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.io.Reader;
import java.nio.charset.Charset;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.text.SimpleDateFormat;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
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
import org.springframework.data.mongodb.core.ReactiveMongoOperations;
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
	private static DateTimeFormatter DATE_FORMAT2 = DateTimeFormatter.ofPattern("yyyy/MM/dd");

	private final MongoOperations operations;
	private final ReactiveMongoOperations reactiveOperations;
	private final AmsDataRepository amsDataRepository;
	private final AdTableRepository adTableRepository;
	private final EbookRoyaltyRepository ebookRoyaltyRepository;

	LoaderService(MongoOperations operations, ReactiveMongoOperations reactiveOperations, AmsDataRepository amsDataRepository,
				  AdTableRepository adTableRepository, EbookRoyaltyRepository ebookRoyaltyRepository) {

		this.operations = operations;
		this.reactiveOperations = reactiveOperations;
		this.amsDataRepository = amsDataRepository;
		this.adTableRepository = adTableRepository;
		this.ebookRoyaltyRepository = ebookRoyaltyRepository;
	}

	Mono<Void> importMagicSpreadsheet(FilePart excelWorkbook) {

		return uploadFile(excelWorkbook, UPLOAD_ROOT)
			.then(toInputStream(excelWorkbook, UPLOAD_ROOT))
			.flatMap(this::loadMagicSpreadsheet)
			.then();

		// TODO Delete file afterward
	}

	Mono<Void> importAmsReport(FilePart csvFilePart, LocalDate date) {

		return uploadFile(csvFilePart, UPLOAD_ROOT)
			.then(toReader(csvFilePart.filename(), UPLOAD_ROOT))
			.flatMap(reader -> loadAmsReport(reader, date))
			.then();
	}


	Mono<Void> importKdpRoyaltyReport(FilePart kdpFilePart) {

		return uploadFile(kdpFilePart, UPLOAD_ROOT)
			.then(toInputStream(kdpFilePart, UPLOAD_ROOT))
			.flatMap(this::loadKdpRoyaltyReport)
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
						LocalDate.parse(EbookRoyaltyDataColumn.RoyaltyDate.stringValue(row)),
						EbookRoyaltyDataColumn.Title.stringValue(row),
						EbookRoyaltyDataColumn.AuthorName.stringValue(row),
						EbookRoyaltyDataColumn.ASIN.stringValue(row),
						EbookRoyaltyDataColumn.Marketplace.stringValue(row),
						EbookRoyaltyDataColumn.RoyaltyType.stringValue(row),
						EbookRoyaltyDataColumn.TransationType.stringValue(row),
						EbookRoyaltyDataColumn.NetUnitsSold.cellType(row) == Cell.CELL_TYPE_NUMERIC ? EbookRoyaltyDataColumn.NetUnitsSold.numericValue(row) : Double.parseDouble(EbookRoyaltyDataColumn.NetUnitsSold.stringValue(row)),
						EbookRoyaltyDataColumn.Royalty.cellType(row) == Cell.CELL_TYPE_NUMERIC ? EbookRoyaltyDataColumn.Royalty.numericValue(row) : Double.parseDouble(EbookRoyaltyDataColumn.Royalty.stringValue(row)),
						EbookRoyaltyDataColumn.Currency.stringValue(row));
				} catch (IllegalStateException|IllegalArgumentException|DateTimeParseException e) {
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
						LocalDate.parse(KenpReadDataColumn.OrderDate.stringValue(row)),
						KenpReadDataColumn.Title.stringValue(row),
						KenpReadDataColumn.AuthorName.stringValue(row),
						KenpReadDataColumn.ASIN.stringValue(row),
						KenpReadDataColumn.Marketplace.stringValue(row),
						KenpReadDataColumn.PagesRead.numericValue(row));
				} catch (IllegalStateException|IllegalArgumentException|DateTimeParseException e) {
					log.error("Failed to parse " + KENP_READ_DATA.name() + ": rowNum=" + row.getRowNum());
					return null;
				}
			})
			.filter(Objects::nonNull)
			.forEach(operations::insert);

		return Mono.empty();
	}

	Mono<Void> loadKdpRoyaltyReport(InputStream inputStream) {

		return Mono.fromRunnable(() -> {

			Workbook workbook = null;
			try {
				workbook = new XSSFWorkbook(inputStream);
			} catch (IOException e) {
				throw new RuntimeException(e);
			}

			log.info("Loading eBook Royalty data...");

			EBOOK_ROYALTY.stream(workbook)
				.filter(row -> row.getCell(KdpRoyaltyEbookRoyaltyColumn.Title.index()) != null && KdpRoyaltyEbookRoyaltyColumn.Title.cellType(row) != Cell.CELL_TYPE_BLANK)
				.map(row -> {
					try {
						return new EbookRoyaltyDataObject(
							null,
							row.getRowNum(),
							LocalDate.parse(KdpRoyaltyEbookRoyaltyColumn.RoyaltyDate.stringValue(row)),
							KdpRoyaltyEbookRoyaltyColumn.Title.stringValue(row),
							KdpRoyaltyEbookRoyaltyColumn.AuthorName.stringValue(row),
							KdpRoyaltyEbookRoyaltyColumn.ASIN.stringValue(row),
							KdpRoyaltyEbookRoyaltyColumn.Marketplace.stringValue(row),
							KdpRoyaltyEbookRoyaltyColumn.RoyaltyType.stringValue(row),
							KdpRoyaltyEbookRoyaltyColumn.TransactionType.stringValue(row),
							KdpRoyaltyEbookRoyaltyColumn.NetUnitsSold.cellType(row) == Cell.CELL_TYPE_NUMERIC ? KdpRoyaltyEbookRoyaltyColumn.NetUnitsSold.numericValue(row) : Double.parseDouble(KdpRoyaltyEbookRoyaltyColumn.NetUnitsSold.stringValue(row)),
							KdpRoyaltyEbookRoyaltyColumn.Royalty.cellType(row) == Cell.CELL_TYPE_NUMERIC ? KdpRoyaltyEbookRoyaltyColumn.Royalty.numericValue(row) : Double.parseDouble(KdpRoyaltyEbookRoyaltyColumn.Royalty.stringValue(row)),
							KdpRoyaltyEbookRoyaltyColumn.Currency.stringValue(row));
					} catch (IllegalStateException|IllegalArgumentException|DateTimeParseException e) {
						log.error("Failed to parse " + EBOOK_ROYALTY.name() + ": rowNum=" + row.getRowNum() + " " + e.getMessage());
						return null;
					}
				})
				.filter(Objects::nonNull)
				.filter(ebookRoyaltyDataObject -> !ebookRoyaltyDataObject.getTransactionType().contains("Free"))
				.forEach(object -> {
						EbookRoyaltyDataObject item = operations.findOne(query(where("title").is(object.getTitle()).and("royaltyDate").is(object.getRoyaltyDate())), EbookRoyaltyDataObject.class);

						if (item == null) {
							log.info("Nothing found! Adding " + object);
							operations.insert(object);
						} else {
							log.info("Already found royalty statement for " + object.getTitle() + " on " + object.getRoyaltyDate() + ". Updating...");
							item.setRoyalty(object.getRoyalty());
							item.setNetUnitsSold(object.getNetUnitsSold());
							item.setRoyaltyType(object.getRoyaltyType());
							operations.save(item);
						}
					});

			log.info("Loading KENP Read data...");

			KENP_READ.stream(workbook)
				.filter(row -> row.getCell(KdpRoyaltyKenpPageReadsColumn.Title.index()) != null && !KdpRoyaltyKenpPageReadsColumn.Title.stringValue(row).equals(""))
				.map(row -> {
					try {
						LocalDate date = LocalDate.parse(KdpRoyaltyKenpPageReadsColumn.Date.stringValue(row));
						String title = KdpRoyaltyKenpPageReadsColumn.Title.stringValue(row);
						String author = KdpRoyaltyKenpPageReadsColumn.AuthorName.stringValue(row);
						String asin = KdpRoyaltyKenpPageReadsColumn.ASIN.stringValue(row);
						String marketPlace = KdpRoyaltyKenpPageReadsColumn.Marketplace.stringValue(row);
						double pagesRead = KdpRoyaltyKenpPageReadsColumn.PagesRead.numericValue(row);
						return new KenpReadDataObject(
							null,
							row.getRowNum(),
							date,
							title,
							author,
							asin,
							marketPlace,
							pagesRead);
					} catch (IllegalStateException|IllegalArgumentException|DateTimeParseException e) {
						log.error("Failed to parse " + KENP_READ.name() + ": rowNum=" + row.getRowNum() + " " + e.getMessage());
						return null;
					}
				})
				.filter(Objects::nonNull)
				.forEach(operations::insert);

		});
	}
	
	Mono<Void> loadAmsReport(Reader reader, LocalDate date) {

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
							csvRecord.get(0), // This column's header fluctuates.
							csvRecord.get("Campaign Name"),
							csvRecord.get("Type"),
							LocalDate.parse(csvRecord.get("Start Date"), DATE_FORMAT2),
							toOptionalDate(csvRecord.get("End Date")),
							toDouble(csvRecord.get("Budget")),
							toDouble(csvRecord.get("Spend")),
							toOptionalDouble(csvRecord.get("Impressions")),
							toOptionalDouble(csvRecord.get("Clicks")),
							toOptionalDouble(csvRecord.get("Average CPC")),
							date));
					} catch (DateTimeParseException e) {
						log.error("Unable to parse #" + csvRecord.getRecordNumber() + " " + csvRecord.toString() + " => " + e.getMessage());
						return Mono.empty();
					}
				})
				.log("importAms-zipWithLatestAmsRecord")
//				.filterWhen(amsDataObject -> adTableRepository.existsByCampaignName(amsDataObject.getCampaignName()))
				.flatMap(amsDataObject -> Mono.zip(
					totalImpressions(amsDataObject.getCampaignName()),
					totalClicks(amsDataObject.getCampaignName()),
					Mono.just(amsDataObject)))
				.log("importAms-create-newAmsRecord")
				.map(tuple3 -> {
					log.info("Total impressions for " + tuple3.getT3().getCampaignName() + " so far => " + tuple3.getT1());
					log.info("Total clicks for " + tuple3.getT3().getCampaignName() + " so far => " + tuple3.getT2());
					log.info("New for " + tuple3.getT3().getCampaignName() + " => " + tuple3.getT3().toString());
					return new AmsDataObject(
						null,
						-1,
						tuple3.getT3().getStatus(),
						tuple3.getT3().getCampaignName(),
						tuple3.getT3().getType(),
						tuple3.getT3().getStartDate(),
						tuple3.getT3().getEndDate(),
						tuple3.getT3().getBudget(),
						tuple3.getT3().getTotalSpend(),
						Optional.of(tuple3.getT3().getImpressions().orElse(0.0) - tuple3.getT1()),
						Optional.of(tuple3.getT3().getClicks().orElse(0.0) - tuple3.getT2()),
						tuple3.getT3().getAverageCpc(),
						date);
				})
				.log("importAms-logitall")
				.log("importAms-saveToMongoDB")
				.flatMap(amsDataRepository::save)
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

	private Mono<Double> totalImpressions(String campaignName) {

		return amsDataRepository.findByCampaignName(campaignName)
			.reduce(0.0, (total, amsDataObject) -> total + amsDataObject.getImpressions().orElse(0.0));
	}

	private Mono<Double> totalClicks(String campaignName) {

		return amsDataRepository.findByCampaignName(campaignName)
			.reduce(0.0, (total, amsDataObject) -> total + amsDataObject.getClicks().orElse(0.0));
	}

	private static int toInt(long value) {
		return new Long(value).intValue();
	}

	private static double toDouble(String value) {

		try {
			return Double.parseDouble(value.replaceAll(",", ""));
		} catch (NumberFormatException e) {
			log.error("Failed to parse '" + value + "' => " + e.getMessage());
			return 0.0;
		}
	}
	private static Optional<Double> toOptionalDouble(String value) {

		try {
			return Optional.of(Double.parseDouble(value.replaceAll(",", "")));
		} catch (NumberFormatException e) {
			log.error("Failed to parse '" + value + "' => " + e.getMessage());
			return Optional.empty();
		}
	}

	private static Optional<LocalDate> toOptionalDate(String value) {

		try {
			return Optional.of(LocalDate.parse(value, DATE_FORMAT2));
		} catch (DateTimeParseException e) {
			return Optional.empty();
		}
	}

	private static Mono<Void> uploadFile(FilePart file, String rootDir) {

		return toFile(file, rootDir)
			.log("uploadFile-fullPath")
			.map(destFile -> {
				try {

					File directory = new File(rootDir);
					if (!directory.exists()) {
						directory.mkdirs();
					}

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
	private static <T> Mono<T> validate(T item, Supplier<Boolean> validationRule) {
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
				return Mono.just(Files.newBufferedReader(Paths.get(rootDir, filename), Charset.forName("ISO-8859-1")));
			} catch (IOException e) {
				throw new RuntimeException(e);
			}
		});
	}

	Mono<Void> deleteAll() {

		return Mono.when(
			reactiveOperations.dropCollection(AmsDataObject.class),
			reactiveOperations.dropCollection(AdTableObject.class),
			reactiveOperations.dropCollection(EbookRoyaltyDataObject.class),
			reactiveOperations.dropCollection(Book.class),
			reactiveOperations.dropCollection(KenpReadDataObject.class));
	}
}
