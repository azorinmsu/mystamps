/*
 * Copyright (C) 2009-2018 Slava Semushin <slava.semushin@gmail.com>
 *
 * This program is free software; you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation; either version 2 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program; if not, write to the Free Software
 * Foundation, Inc., 51 Franklin Street, Fifth Floor, Boston, MA 02110-1301, USA.
 */
package ru.mystamps.web.service;

import java.math.BigDecimal;
import java.util.Arrays;
import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;
import java.util.stream.IntStream;
import java.util.stream.Stream;

import org.apache.commons.lang3.StringUtils;

import org.slf4j.Logger;

import org.springframework.transaction.annotation.Transactional;

import lombok.RequiredArgsConstructor;

import ru.mystamps.web.dao.dto.Currency;
import ru.mystamps.web.service.dto.RawParsedDataDto;
import ru.mystamps.web.service.dto.SeriesExtractedInfo;
import ru.mystamps.web.validation.ValidationRules;

@RequiredArgsConstructor
@SuppressWarnings({ "PMD.TooManyMethods", "PMD.GodClass" })
public class SeriesInfoExtractorServiceImpl implements SeriesInfoExtractorService {
	
	// Related to RELEASE_YEAR_REGEXP and used in unit tests.
	protected static final int MAX_SUPPORTED_RELEASE_YEAR = 2099;
	
	// Regular expression matches release year of the stamps (from 1840 till 2099).
	private static final Pattern RELEASE_YEAR_REGEXP =
		Pattern.compile("(18[4-9][0-9]|19[0-9]{2}|20[0-9]{2})г?");
	
	// Regular expression matches number of the stamps in a series (from 1 to 99).
	private static final Pattern NUMBER_OF_STAMPS_REGEXP = Pattern.compile(
		"([1-9][0-9]?)( беззубцовые)? мар(ок|ки)",
		Pattern.CASE_INSENSITIVE | Pattern.UNICODE_CASE
	);
	
	// Regular expression matches range of Michel catalog numbers (from 1 to 9999).
	private static final Pattern MICHEL_NUMBERS_REGEXP =
		Pattern.compile("#[ ]?([1-9][0-9]{0,3})-([1-9][0-9]{0,3})");
	
	// CheckStyle: ignore LineLength for next 4 lines
	private static final Pattern VALID_CATEGORY_NAME_EN = Pattern.compile(ValidationRules.CATEGORY_NAME_EN_REGEXP);
	private static final Pattern VALID_CATEGORY_NAME_RU = Pattern.compile(ValidationRules.CATEGORY_NAME_RU_REGEXP);
	private static final Pattern VALID_COUNTRY_NAME_EN  = Pattern.compile(ValidationRules.COUNTRY_NAME_EN_REGEXP);
	private static final Pattern VALID_COUNTRY_NAME_RU  = Pattern.compile(ValidationRules.COUNTRY_NAME_RU_REGEXP);
	
	// Max number of candidates that will be used in the SQL query within IN() statement.
	private static final long MAX_CANDIDATES_FOR_LOOKUP = 50;
	
	private final Logger log;
	private final CategoryService categoryService;
	private final CountryService countryService;
	private final TransactionParticipantService transactionParticipantService;
	
	// @todo #803 SeriesInfoExtractorServiceImpl.extract(): add unit tests
	@Override
	@Transactional(readOnly = true)
	public SeriesExtractedInfo extract(RawParsedDataDto data) {
		List<Integer> categoryIds = extractCategory(data.getCategoryName());
		List<Integer> countryIds = extractCountry(data.getCountryName());
		Integer releaseYear = extractReleaseYear(data.getReleaseYear());
		Integer quantity = extractQuantity(data.getQuantity());
		Boolean perforated = extractPerforated(data.getPerforated());
		Set<String> michelNumbers = extractMichelNumbers(data.getMichelNumbers());
		Integer sellerId = extractSeller(data.getSellerName(), data.getSellerUrl());
		String sellerName = extractSellerName(sellerId, data.getSellerName());
		String sellerUrl = extractSellerUrl(sellerId, data.getSellerUrl());
		BigDecimal price = extractPrice(data.getPrice());
		String currency = extractCurrency(data.getCurrency());
		
		return new SeriesExtractedInfo(
			categoryIds,
			countryIds,
			releaseYear,
			quantity,
			perforated,
			michelNumbers,
			sellerId,
			sellerName,
			sellerUrl,
			price,
			currency
		);
	}
	
	// CheckStyle: ignore LineLength for next 1 line
	// @todo #821 SeriesInfoExtractorServiceImpl.extractCategory(): add unit tests for filtering invalid names
	protected List<Integer> extractCategory(String fragment) {
		if (StringUtils.isBlank(fragment)) {
			return Collections.emptyList();
		}
		
		log.debug("Determining category from a fragment: '{}'", fragment);
		
		String[] names = StringUtils.split(fragment, "\n\t ,.");
		List<String> candidates = Arrays.stream(names)
			.filter(SeriesInfoExtractorServiceImpl::validCategoryName)
			.distinct()
			.limit(MAX_CANDIDATES_FOR_LOOKUP)
			.collect(Collectors.toList());
		
		log.debug("Possible candidates: {}", candidates);
		
		List<Integer> categories = categoryService.findIdsByNames(candidates);
		log.debug("Found categories: {}", categories);
		if (!categories.isEmpty()) {
			return categories;
		}
		
		for (String candidate : candidates) {
			log.debug("Possible candidate: '{}%'", candidate);
			categories = categoryService.findIdsWhenNameStartsWith(candidate);
			if (!categories.isEmpty()) {
				log.debug("Found categories: {}", categories);
				return categories;
			}
		}
		
		log.debug("Could not extract category from a fragment");
		
		return Collections.emptyList();
	}
	
	// CheckStyle: ignore LineLength for next 1 line
	// @todo #821 SeriesInfoExtractorServiceImpl.extractCountry(): add unit tests for filtering invalid names
	protected List<Integer> extractCountry(String fragment) {
		if (StringUtils.isBlank(fragment)) {
			return Collections.emptyList();
		}
		
		log.debug("Determining country from a fragment: '{}'", fragment);
		
		String[] words = StringUtils.split(fragment, "\n\t ,.");
		
		Stream<String> names = Arrays.stream(words);
		
		// Generate more candidates by split their names by a hyphen.
		// For example: "Minerals-Maldives" becomes [ "Minerals", "Maldives" ]
		Stream<String> additionalNames = Arrays.stream(words)
			.filter(el -> el.contains("-"))
			.map(el -> StringUtils.split(el, '-'))
			.flatMap(Arrays::stream);
		
		List<String> candidates = Stream.concat(names, additionalNames)
			.filter(SeriesInfoExtractorServiceImpl::validCountryName)
			.distinct()
			.limit(MAX_CANDIDATES_FOR_LOOKUP)
			.collect(Collectors.toList());
		
		log.debug("Possible candidates: {}", candidates);
		
		List<Integer> countries = countryService.findIdsByNames(candidates);
		log.debug("Found countries: {}", countries);
		if (!countries.isEmpty()) {
			return countries;
		}
		
		for (String candidate : candidates) {
			log.debug("Possible candidate: '{}%'", candidate);
			countries = countryService.findIdsWhenNameStartsWith(candidate);
			if (!countries.isEmpty()) {
				log.debug("Found countries: {}", countries);
				return countries;
			}
		}
		
		log.debug("Could not extract country from a fragment");
		
		return Collections.emptyList();
	}
	
	protected Integer extractReleaseYear(String fragment) {
		if (StringUtils.isBlank(fragment)) {
			return null;
		}
		
		log.debug("Determining release year from a fragment: '{}'", fragment);
		
		String[] candidates = StringUtils.split(fragment);
		for (String candidate : candidates) {
			Matcher matcher = RELEASE_YEAR_REGEXP.matcher(candidate);
			if (!matcher.matches()) {
				continue;
			}
			
			try {
				Integer year = Integer.valueOf(matcher.group(1));
				log.debug("Release year is {}", year);
				return year;
				
			} catch (NumberFormatException ignored) { // NOPMD: EmptyCatchBlock
				// continue with the next element
			}
		}
		
		log.debug("Could not extract release year from a fragment");
		
		return null;
	}
	
	// @todo #781 SeriesInfoExtractorServiceImpl.extractQuantity() respect MAX_STAMPS_IN_SERIES
	protected Integer extractQuantity(String fragment) {
		if (StringUtils.isBlank(fragment)) {
			return null;
		}
		
		log.debug("Determining quantity from a fragment: '{}'", fragment);
		
		Matcher matcher = NUMBER_OF_STAMPS_REGEXP.matcher(fragment);
		if (matcher.find()) {
			String quantity = matcher.group(1);
			log.debug("Quantity is {}", quantity);
			return Integer.valueOf(quantity);
		}
		
		log.debug("Could not extract quantity from a fragment");
		
		return null;
	}
	
	// @todo #782 Series import: add integration test for extracting perforation flag
	protected Boolean extractPerforated(String fragment) {
		if (StringUtils.isBlank(fragment)) {
			return null;
		}
		
		log.debug("Determining perforation from a fragment: '{}'", fragment);
		
		boolean withoutPerforation = StringUtils.containsIgnoreCase(fragment, "б/з")
			|| StringUtils.containsIgnoreCase(fragment, "беззубцовые");
		if (withoutPerforation) {
			log.debug("Perforation is false");
			return Boolean.FALSE;
		}
		
		log.debug("Could not extract perforation info from a fragment");
		
		return null;
	}
	
	// @todo #694 SeriesInfoExtractorServiceImpl.extractMichelNumbers(): add unit tests
	// @todo #694 SeriesInfoExtractorServiceImpl: support for a single Michel number
	// @todo #694 SeriesInfoExtractorServiceImpl: support for a comma separated Michel numbers
	protected Set<String> extractMichelNumbers(String fragment) {
		if (StringUtils.isBlank(fragment)) {
			return Collections.emptySet();
		}
		
		log.debug("Determining michel numbers from a fragment: '{}'", fragment);
		
		Matcher matcher = MICHEL_NUMBERS_REGEXP.matcher(fragment);
		if (matcher.find()) {
			Integer begin = Integer.valueOf(matcher.group(1));
			Integer end = Integer.valueOf(matcher.group(2));
			if (begin < end) {
				Set<String> numbers = IntStream.rangeClosed(begin, end)
					.mapToObj(String::valueOf)
					.collect(Collectors.toCollection(LinkedHashSet::new));
				log.debug("Extracted michel numbers: {}", numbers);
				return numbers;
			}
		}
		
		log.debug("Could not extract michel numbers from a fragment");
		
		return Collections.emptySet();
	}
	
	public Integer extractSeller(String name, String url) {
		if (StringUtils.isBlank(name) || StringUtils.isBlank(url)) {
			return null;
		}
		
		// @todo #695 SeriesInfoExtractorServiceImpl.extractSeller(): validate name/url (length etc)
		
		log.debug("Determining seller by name '{}' and url '{}'", name, url);
		
		Integer sellerId = transactionParticipantService.findSellerId(name, url);
		if (sellerId != null) {
			log.debug("Found seller: #{}", sellerId);
			return sellerId;
		}
		
		log.debug("Could not extract seller based on name/url");
		
		return null;
	}
	
	// @todo #695 SeriesInfoExtractorServiceImpl.extractSellerName(): add unit tests
	protected String extractSellerName(Integer id, String name) {
		if (id != null) {
			return null;
		}
		
		// @todo #695 SeriesInfoExtractorServiceImpl.extractSellerName(): filter out short names
		// @todo #695 SeriesInfoExtractorServiceImpl.extractSellerName(): filter out long names
		
		// we need a name ony if we couldn't find a seller in database (id == null)
		return name;
	}
	
	// @todo #695 SeriesInfoExtractorServiceImpl.extractSellerUrl(): add unit tests
	protected String extractSellerUrl(Integer id, String url) {
		if (id != null) {
			return null;
		}

		// @todo #695 SeriesInfoExtractorServiceImpl.extractSellerUrl(): filter out non-urls
		// @todo #695 SeriesInfoExtractorServiceImpl.extractSellerUrl(): filter out too long urls
		
		// we need a url ony if we couldn't find a seller in database (id == null)
		return url;
	}
	
	public BigDecimal extractPrice(String fragment) {
		if (StringUtils.isBlank(fragment)) {
			return null;
		}
		
		log.debug("Determining price from a fragment: '{}'", fragment);
		
		try {
			BigDecimal price = new BigDecimal(fragment);
			log.debug("Price is {}", price);
			// @todo #695 SeriesInfoExtractorServiceImpl.extractPrice(): filter out values <= 0
			return price;
			
		} catch (NumberFormatException ex) {
			log.debug("Could not extract price: {}", ex.getMessage());
			return null;
		}
	}
	
	public String extractCurrency(String fragment) {
		if (StringUtils.isBlank(fragment)) {
			return null;
		}
		
		log.debug("Determining currency from a fragment: '{}'", fragment);
		
		try {
			Currency currency = Enum.valueOf(Currency.class, fragment);
			log.debug("Currency is {}", currency);
			return currency.toString();
			
		} catch (IllegalArgumentException ex) {
			log.debug("Could not extract currency: {}", ex.getMessage());
			return null;
		}
	}
	
	private static boolean validCategoryName(String name) {
		if (name.length() < ValidationRules.CATEGORY_NAME_MIN_LENGTH) {
			return false;
		}
		if (name.length() > ValidationRules.CATEGORY_NAME_MAX_LENGTH) {
			return false;
		}
		return VALID_CATEGORY_NAME_EN.matcher(name).matches()
			|| VALID_CATEGORY_NAME_RU.matcher(name).matches();
	}
	
	private static boolean validCountryName(String name) {
		if (name.length() < ValidationRules.COUNTRY_NAME_MIN_LENGTH) {
			return false;
		}
		if (name.length() > ValidationRules.COUNTRY_NAME_MAX_LENGTH) {
			return false;
		}
		return VALID_COUNTRY_NAME_EN.matcher(name).matches()
			|| VALID_COUNTRY_NAME_RU.matcher(name).matches();
	}
	
}
