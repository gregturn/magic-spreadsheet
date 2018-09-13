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
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseBody;

/**
 * @author Greg Turnquist
 */
@Controller
@Slf4j
class AdController {

	private final AmsDataRepository amsDataRepository;
	private final AdTableRepository adTableRepository;
	private final AdService adService;
	private final BookRepository bookRepository;

	AdController(AmsDataRepository amsDataRepository,
				 AdTableRepository adTableRepository,
				 AdService adService,
				 BookRepository bookRepository) {

		this.amsDataRepository = amsDataRepository;
		this.adTableRepository = adTableRepository;
		this.adService = adService;
		this.bookRepository = bookRepository;
	}

	@GetMapping("/ads")
	Mono<String> ads(Model model) {

		model.addAttribute("adTable", adTableRepository.findAll().map(AdTableDTO::new));

		return Mono.just("ads");
	}

	@GetMapping("/conversions")
	Mono<String> conversions(@RequestParam(name = "window", required = false) Optional<String> optionalWindow, Model model) {

		String window = optionalWindow.orElse("all");

		model.addAttribute("filterOptions", Arrays.asList(
			new FilterOption("all", "Lifetime", window.equals("all")),
			new FilterOption("90days", "Last 90 days", window.equals("90days")),
			new FilterOption("45days", "Last 45 days", window.equals("45days")),
			new FilterOption("30days", "Last 30 days", window.equals("30days")),
			new FilterOption("15days", "Last 15 days", window.equals("15days"))
		));

		if ("all".equals(window)) {

			model.addAttribute("conversionData",
				adService.clicksToConvert(Optional.empty())
					.sort((o1, o2) -> Double.compare(o2.getRawROI(), o1.getRawROI())));

		} else if ("90days".equals(window)) {

			model.addAttribute("conversionData",
				adService.clicksToConvert(Optional.of(LocalDate.now().minusDays(90)))
					.sort((o1, o2) -> Double.compare(o2.getRawROI(), o1.getRawROI())));

		} else if ("45days".equals(window)) {

			model.addAttribute("conversionData",
				adService.clicksToConvert(Optional.of(LocalDate.now().minusDays(45)))
					.sort((o1, o2) -> Double.compare(o2.getRawROI(), o1.getRawROI())));

		} else if ("30days".equals(window)) {

			model.addAttribute("conversionData",
				adService.clicksToConvert(Optional.of(LocalDate.now().minusDays(30)))
					.sort((o1, o2) -> Double.compare(o2.getRawROI(), o1.getRawROI())));

		} else if ("15days".equals(window)) {

			model.addAttribute("conversionData",
				adService.clicksToConvert(Optional.of(LocalDate.now().minusDays(15)))
					.sort((o1, o2) -> Double.compare(o2.getRawROI(), o1.getRawROI())));
		}

		return Mono.just("conversions");
	}

	@GetMapping("/seriesConversions")
	Mono<String> seriesConversions(@RequestParam(name = "window", required = false) Optional<String> optionalWindow, Model model) {

		String window = optionalWindow.orElse("all");

		model.addAttribute("filterOptions", Arrays.asList(
			new FilterOption("all", "Lifetime", window.equals("all")),
			new FilterOption("90days", "Last 90 days", window.equals("90days")),
			new FilterOption("45days", "Last 45 days", window.equals("45days")),
			new FilterOption("30days", "Last 30 days", window.equals("30days")),
			new FilterOption("15days", "Last 15 days", window.equals("15days"))
		));

		if ("all".equals(window)) {

			model.addAttribute("conversionData",
				adService.clicksToConvertPerSeries(Optional.empty())
					.sort((o1, o2) -> Double.compare(o2.getRawROI(), o1.getRawROI())));

		} else if ("90days".equals(window)) {

			model.addAttribute("conversionData",
				adService.clicksToConvertPerSeries(Optional.of(LocalDate.now().minusDays(90)))
					.sort((o1, o2) -> Double.compare(o2.getRawROI(), o1.getRawROI())));

		} else if ("45days".equals(window)) {

			model.addAttribute("conversionData",
				adService.clicksToConvertPerSeries(Optional.of(LocalDate.now().minusDays(45)))
					.sort((o1, o2) -> Double.compare(o2.getRawROI(), o1.getRawROI())));

		} else if ("30days".equals(window)) {

			model.addAttribute("conversionData",
				adService.clicksToConvertPerSeries(Optional.of(LocalDate.now().minusDays(30)))
					.sort((o1, o2) -> Double.compare(o2.getRawROI(), o1.getRawROI())));

		} else if ("15days".equals(window)) {

			model.addAttribute("conversionData",
				adService.clicksToConvertPerSeries(Optional.of(LocalDate.now().minusDays(15)))
					.sort((o1, o2) -> Double.compare(o2.getRawROI(), o1.getRawROI())));
		}

		return Mono.just("seriesConversions");
	}

	@GetMapping("/adChart/{title}")
	Mono<String> adPerformance(@PathVariable String title, Model model) {

		model.addAttribute("stats", adTableRepository.findByBookTitle(title)
			.map(AdTableObject::getCampaignName)
			.flatMap(amsDataRepository::findByCampaignName)
			.sort(Comparator.comparing(AmsDataObject::getDate))
			.filter(amsDataObject -> amsDataObject.getClicks().orElse(0.0) > 0.0)
			.filter(amsDataObject -> amsDataObject.getImpressions().orElse(0.0) > 0.0)
			.filter(amsDataObject -> amsDataObject.getDate().isAfter(LocalDate.parse("2018-05-27")))
			.map(AmsDataDTO::new));

		return Mono.just("adChart");
	}

	@GetMapping("/conversionsRaw")
	@ResponseBody
	Flux<BookDTO> conversionsRaw() {

		return adService.clicksToConvert(Optional.empty())
			.sort(Comparator.comparing(BookDTO::getTitle));
	}

	@GetMapping("/createAllAds")
	Mono<String> createAllAds() {

		return adService.unlinkedAmsData()
			.map(Utils::amsDataToAdData)
			.sort(Comparator.comparing(AdTableObject::getCampaignName))
			.distinct()
			.flatMap(adTableRepository::save)
			.then(Mono.just("redirect:/unlinkedAds"));
	}

	@DeleteMapping("/deleteAllAdData")
	Mono<String> deleteAllAdData() {

		return adTableRepository.deleteAll()
			.thenReturn("redirect:/ads");
	}

	@GetMapping("/createAd")
	Mono<String> createAd(@RequestParam("id") String id) {

		return amsDataRepository.findById(id)
			.map(Utils::amsDataToAdData)
			.flatMap(adTableRepository::save)
			.then(Mono.just("redirect:/unlinkedAds"));
	}
}
