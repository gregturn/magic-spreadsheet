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

import static org.mockito.BDDMockito.*;

import lombok.extern.slf4j.Slf4j;

import java.time.Duration;
import java.time.LocalDate;
import java.util.Arrays;
import java.util.Optional;

import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.Mock;
import reactor.core.publisher.Flux;
import reactor.test.StepVerifier;
import reactor.util.function.Tuples;
import org.springframework.test.context.junit4.SpringJUnit4ClassRunner;

/**
 * @author Greg Turnquist
 */
@Slf4j
@RunWith(SpringJUnit4ClassRunner.class)
public class UtilsTests {

	LocalDate date1 = LocalDate.parse("2018-08-11");
	LocalDate date2 = LocalDate.parse("2018-08-13");
	LocalDate date3 = LocalDate.parse("2018-08-15");

	@Mock
	EbookRoyaltyRepository royaltyRepository;

	@Mock
	AmsDataRepository amsDataRepository;

	@Before
	public void setUp() {

		given(royaltyRepository.findByTitleLike("Test Book")).willReturn(Flux.just(
			new EbookRoyaltyDataObject(
				null,
				-1,
				date1,
				"Test Book",
				null,
				null,
				null,
				null,
				null,
				1.0,
				1.99,
				null
			),
			new EbookRoyaltyDataObject(
				null,
				-1,
				date2,
				"Test Book",
				null,
				null,
				null,
				null,
				null,
				1.0,
				1.99,
				null
			),
			new EbookRoyaltyDataObject(
				null,
				-1,
				date3,
				"Test Book",
				null,
				null,
				null,
				null,
				null,
				1.0,
				1.99,
				null
			)
			));

		given(amsDataRepository.findByCampaignNameAndDateBetween("Test Book", date1, date2)).willReturn(Flux.just(
			new AmsDataObject(
				null,
				-1,
				null,
				null,
				null,
				null,
				null,
				0.0,
				0.0,
				null,
				null,
				null,
				Optional.of(99.0),
				null,
				date1,
				Optional.empty(),
				Optional.empty()
			),
			new AmsDataObject(
				null,
				-1,
				null,
				null,
				null,
				null,
				null,
				0.0,
				0.0,
				null,
				null,
				null,
				Optional.of(1.0),
				null,
				date1.plusDays(1),
				Optional.empty(),
				Optional.empty()
			),
			new AmsDataObject(
				null,
				-1,
				null,
				null,
				null,
				null,
				null,
				0.0,
				0.0,
				null,
				null,
				null,
				Optional.of(17.0),
				null,
				date1.plusDays(2),
				Optional.empty(),
				Optional.empty()
			)
		));

		given(amsDataRepository.findByCampaignNameAndDateBetween("Test Book", date2, date3)).willReturn(Flux.just(
			new AmsDataObject(
				null,
				-1,
				null,
				null,
				null,
				null,
				null,
				0.0,
				0.0,
				null,
				null,
				null,
				Optional.of(89.0),
				null,
				date2,
				Optional.empty(),
				Optional.empty()
			),
			new AmsDataObject(
				null,
				-1,
				null,
				null,
				null,
				null,
				null,
				0.0,
				0.0,
				null,
				null,
				null,
				Optional.of(1.0),
				null,
				date2.plusDays(1),
				Optional.empty(),
				Optional.empty()
			),
			new AmsDataObject(
				null,
				-1,
				null,
				null,
				null,
				null,
				null,
				0.0,
				0.0,
				null,
				null,
				null,
				Optional.of(16.0),
				null,
				date2.plusDays(2),
				Optional.empty(),
				Optional.empty()
			)
		));
	}

	@Test
	public void intervals() {

		StepVerifier.create(Utils.clicksPerSale(royaltyRepository, amsDataRepository, "Test Book"))
			.expectNext(Arrays.asList(
				Tuples.of(117.0, date2),
				Tuples.of(106.0, date3)))
			.expectComplete()
			.verify(Duration.ofSeconds(30));
	}

}
