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

import static com.greglturnquist.magicspreadsheet.Utils.*;

import lombok.extern.slf4j.Slf4j;

import java.util.Comparator;

import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.PostMapping;

/**
 * @author Greg Turnquist
 */
@Controller
@Slf4j
class BookController {

	private final BookRepository bookRepository;
	private final AdTableRepository adTableRepository;
	private final AdService adService;

	BookController(BookRepository bookRepository, AdTableRepository adTableRepository, AdService adService) {

		this.bookRepository = bookRepository;
		this.adTableRepository = adTableRepository;
		this.adService = adService;
	}
	
	@GetMapping("/books")
	Mono<String> allBooks(Model model) {

		model.addAttribute("books", bookRepository.findAll()
			.sort(Comparator.comparing(Book::getTitle)));

		return Mono.just("books");
	}

	@PostMapping("/setBookShort")
	Mono<String> setBookShort(@ModelAttribute BookAndShort bookShort) {

		return bookRepository.findByTitle(bookShort.getBookTitle())
			.map(book -> {
				book.setBookShort(bookShort.getBookShort());
				book.setSeries(bookShort.getBookSeries());
				book.setKENPC(bookShort.getKenpc());
				return book;
			})
			.flatMap(bookRepository::save)
			.then(Mono.just("redirect:/books"));
	}

	@GetMapping("/createAllBooks")
	Mono<String> createAllBooks() {

		return adService.unlinkedRoyalties()
			.map(Utils::royaltyToBook)
			.sort(Comparator.comparing(Book::getTitle))
			.distinct()
			.flatMap(bookRepository::save)
			.then(Mono.just("redirect:/unlinkedAds"));
	}

	@PostMapping("/createBooks")
	Mono<String> createBooks(@ModelAttribute AdBookLink adLinkingParams) {

		if (adLinkingParams.getBookTitle().contains("bestGuess")) {

			return Flux.fromIterable(adLinkingParams.getAdIds())
				.flatMap(id -> adTableRepository.findById(id)
					.flatMapMany(ad -> bestGuess(bookRepository.findAll(), ad)
						.map(Book::getTitle)
						.flatMap(bookTitle -> bookRepository.findByTitle(bookTitle).zipWith(Mono.just(ad)))))
				.map(objects -> objects.getT2().updateAd(objects.getT1()))
				.flatMap(adTableRepository::save)
				.then(Mono.just("redirect:/unlinkedAds"));
		}

		return bookRepository.findByTitle(adLinkingParams.getBookTitle())
			.switchIfEmpty(bookRepository.save(new Book(
				null,
				-1,
				-1,
				mainTitle(adLinkingParams.getBookTitle()),
				subTitle(adLinkingParams.getBookTitle()),
				adLinkingParams.getAuthor(),
				adLinkingParams.getBookShort(),
				adLinkingParams.getSeries(),
				adLinkingParams.getASIN(),
				adLinkingParams.getKENPC()
			)))
			.flatMapMany(book -> Flux.fromIterable(adLinkingParams.getAdIds())
				.flatMap(id -> adTableRepository.findById(id).zipWith(Mono.just(book))))
			.flatMap(objects -> adTableRepository.save(objects.getT1().updateAd(objects.getT2())))
			.then(Mono.just("redirect:/unlinkedAds"));
	}

	@DeleteMapping("/books")
	Mono<String> deleteBook(@ModelAttribute BookAndShort bookShort) {

		return Mono.when(
			bookRepository.deleteByTitle(bookShort.getBookTitle()),
			wipeOutBookReferencesInAds(bookShort.getBookTitle()))
			.then(Mono.just("redirect:/books"));
	}

	private Mono<Void> wipeOutBookReferencesInAds(String title) {

		return adTableRepository.findByBookTitle(title)
			.flatMap(adTableObject -> {
				adTableObject.setBookTitle("");
				adTableObject.setSeries("");
				return adTableRepository.save(adTableObject);
			})
			.then();
	}
}
