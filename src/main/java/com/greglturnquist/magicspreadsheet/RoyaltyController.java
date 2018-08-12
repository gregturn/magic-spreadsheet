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
import java.util.Arrays;
import java.util.Comparator;
import java.util.Optional;

import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;
import org.springframework.data.domain.Sort;
import org.springframework.http.codec.multipart.FilePart;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RequestPart;

/**
 * @author Greg Turnquist
 */

@Controller
@Slf4j
class RoyaltyController {

	private final EbookRoyaltyRepository ebookRoyaltyRepository;
	private final KenpReadRepository kenpReadRepository;
	private final LoaderService loaderService;

	RoyaltyController(EbookRoyaltyRepository ebookRoyaltyRepository,
					  KenpReadRepository kenpReadRepository,
					  LoaderService loaderService) {
		
		this.ebookRoyaltyRepository = ebookRoyaltyRepository;
		this.kenpReadRepository = kenpReadRepository;
		this.loaderService = loaderService;
	}

	@GetMapping("/rawRoyaltyData")
	Mono<String> rawRoyaltyData(@RequestParam(name = "window", required = false) Optional<String> optionalWindow, Model model) {

		String window = optionalWindow.orElse("all");

		model.addAttribute("filterOptions", Arrays.asList(
			new FilterOption("all", "Lifetime", window.equals("all")),
			new FilterOption("90days", "Last 90 days", window.equals("90days")),
			new FilterOption("30days", "Last 30 days", window.equals("30days")),
			new FilterOption("15days", "Last 15 days", window.equals("15days"))
		));

		Sort sortByDateAndCampaignName = Sort.by("royaltyDate", "title");

		if ("all".equals(window)) {

			model.addAttribute("royaltyData", ebookRoyaltyRepository
				.findAll(sortByDateAndCampaignName)
				.map(EbookRoyaltyDataDTO::new));

		} else if ("90days".equals(window)) {

			model.addAttribute("royaltyData", ebookRoyaltyRepository
				.findByRoyaltyDateAfter(LocalDate.now().minusDays(90), sortByDateAndCampaignName)
				.map(EbookRoyaltyDataDTO::new));

		} else if ("30days".equals(window)) {

			model.addAttribute("royaltyData", ebookRoyaltyRepository
				.findByRoyaltyDateAfter(LocalDate.now().minusDays(30), sortByDateAndCampaignName)
				.map(EbookRoyaltyDataDTO::new));

		} else if ("15days".equals(window)) {

			model.addAttribute("royaltyData", ebookRoyaltyRepository
				.findByRoyaltyDateAfter(LocalDate.now().minusDays(15), sortByDateAndCampaignName)
				.map(EbookRoyaltyDataDTO::new));
		}

		return Mono.just("rawRoyaltyData");
	}

	@GetMapping("/rawKenpData")
	Mono<String> rawKenpData(@RequestParam(name = "window", required = false) Optional<String> optionalWindow, Model model) {

		String window = optionalWindow.orElse("all");

		model.addAttribute("filterOptions", Arrays.asList(
			new FilterOption("all", "Lifetime", window.equals("all")),
			new FilterOption("90days", "Last 90 days", window.equals("90days")),
			new FilterOption("30days", "Last 30 days", window.equals("30days")),
			new FilterOption("15days", "Last 15 days", window.equals("15days"))
		));

		Sort sortByDateAndCampaignName = Sort.by("orderDate", "title");

		if ("all".equals(window)) {

			model.addAttribute("royaltyData", kenpReadRepository
				.findAll(sortByDateAndCampaignName)
				.map(KenReadDataDTO::new));

		} else if ("90days".equals(window)) {

			model.addAttribute("royaltyData", kenpReadRepository
				.findByOrderDateAfter(LocalDate.now().minusDays(90), sortByDateAndCampaignName)
				.map(KenReadDataDTO::new));

		} else if ("30days".equals(window)) {

			model.addAttribute("royaltyData", kenpReadRepository
				.findByOrderDateAfter(LocalDate.now().minusDays(30), sortByDateAndCampaignName)
				.map(KenReadDataDTO::new));

		} else if ("15days".equals(window)) {

			model.addAttribute("royaltyData", kenpReadRepository
				.findByOrderDateAfter(LocalDate.now().minusDays(15), sortByDateAndCampaignName)
				.map(KenReadDataDTO::new));
		}

		return Mono.just("rawKenpData");
	}

	@PostMapping("/import-kdp-royalty")
	Mono<String> importKdpRoyaltyReport(@RequestPart(name = "spreadsheet") Flux<FilePart> kdpReport) {

		return kdpReport
			.sort(Comparator.comparing(FilePart::filename))
			.flatMap(loaderService::importKdpRoyaltyReport)
			.then(Mono.just("redirect:/"));
	}

}
