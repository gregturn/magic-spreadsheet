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

import java.util.Comparator;

import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.ResponseBody;

/**
 * @author Greg Turnquist
 */
@Controller
public class MagicSpreadsheetController {

	private final AmsDataRepository amsDataRepository;
	private final AdTableRepository adTableRepository;
	private final AdService adService;

	public MagicSpreadsheetController(AmsDataRepository amsDataRepository,
									  AdTableRepository adTableRepository,
									  AdService adService) {

		this.amsDataRepository = amsDataRepository;
		this.adTableRepository = adTableRepository;
		this.adService = adService;
	}

	@GetMapping("/")
	Mono<String> index(Model model) {
		return Mono.just("index");
	}

	@GetMapping("/ads")
	Mono<String> ads(Model model) {

		model.addAttribute("adTable", adTableRepository.findAll().map(AdTableDTO::new));

		return Mono.just("ads");
	}

	@GetMapping("/rawdata")
	Mono<String> raw(Model model) {

		model.addAttribute("amsData", amsDataRepository.findAll().map(AmsDataDTO::new));

		return Mono.just("raw");
	}

	@GetMapping("/conversions")
	@ResponseBody
	Flux<BookDTO> conversions() {
		return adService.clicksToConvert()
			.sort(Comparator.comparing(BookDTO::getTitle));
	}
}
