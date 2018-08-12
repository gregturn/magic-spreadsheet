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

import java.time.LocalDate;

import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;
import org.springframework.data.domain.Sort;
import org.springframework.data.mongodb.repository.ReactiveMongoRepository;

/**
 * @author Greg Turnquist
 */
interface AmsDataRepository extends ReactiveMongoRepository<AmsDataObject, String> {

	Flux<AmsDataObject> findByCampaignName(String campaignName);

	Flux<AmsDataObject> findByCampaignNameAndDateAfter(String campaignName, LocalDate date);
	
	Flux<AmsDataObject> findByDateAfter(LocalDate date, Sort sort);

	Flux<AmsDataObject> findByCampaignNameAndDate(String campaignName, LocalDate date);

	Mono<Boolean> existsByCampaignNameAndDate(String campaignName, LocalDate date);

	Mono<Boolean> existsByCampaignNameAndDateAfter(String campaignName, LocalDate date);

	Flux<AmsDataObject> findByCampaignNameAndDateBetween(String campaignName, LocalDate beginning, LocalDate end);
}
