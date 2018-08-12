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
interface KenpReadRepository extends ReactiveMongoRepository<KenpReadDataObject, String> {

	Flux<KenpReadDataObject> findByTitleLike(String title);

	Mono<KenpReadDataObject> findByTitleAndOrderDate(String title, LocalDate date);

	Flux<KenpReadDataObject> findByTitleLikeAndOrderDateAfter(String title, LocalDate date);

	Flux<KenpReadDataObject> findByTitleAndOrderDateBetween(String title, LocalDate earlier, LocalDate later);

	Flux<KenpReadDataObject> findByOrderDateAfter(LocalDate localDate, Sort sortByDateAndCampaignName);
}
