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
package ru.mystamps.web.service.dto;

import lombok.Getter;
import lombok.RequiredArgsConstructor;

@Getter
@RequiredArgsConstructor
public class RawParsedDataDto {
	private final String categoryName;
	private final String countryName;
	private final String imageUrl;
	private final String releaseYear;
	private final String quantity;
	private final String perforated;
	private final String michelNumbers;
	private final String sellerName;
	private final String sellerUrl;
	private final String price;
	private final String currency;
	
	public RawParsedDataDto withImageUrl(String url) {
		return new RawParsedDataDto(
			categoryName,
			countryName,
			url,
			releaseYear,
			quantity,
			perforated,
			michelNumbers,
			sellerName,
			sellerUrl,
			price,
			currency
		);
	}
	
}
