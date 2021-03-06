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
package ru.mystamps.web.feature.series;

import java.util.Collections;
import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.apache.commons.lang3.Validate;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.dao.EmptyResultDataAccessException;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.jdbc.support.GeneratedKeyHolder;
import org.springframework.jdbc.support.KeyHolder;

import lombok.RequiredArgsConstructor;

import ru.mystamps.web.support.jdbc.RowMappers;

// TODO: move stamps related methods to separate interface (#88)
@SuppressWarnings({
	"PMD.AvoidDuplicateLiterals",
	"PMD.TooManyMethods",
	"PMD.TooManyFields",
	"PMD.LongVariable"
})
@RequiredArgsConstructor
public class JdbcSeriesDao implements SeriesDao {
	
	private final NamedParameterJdbcTemplate jdbcTemplate;
	
	@Value("${series.create}")
	private String createSeriesSql;
	
	@Value("${series.mark_as_modified}")
	private String markAsModifiedSql;
	
	@Value("${series.find_all_for_sitemap}")
	private String findAllForSitemapSql;
	
	@Value("${series.find_last_added}")
	private String findLastAddedSeriesSql;
	
	@Value("${series.find_full_info_by_id}")
	private String findFullInfoByIdSql;
	
	@Value("${series.find_by_ids}")
	private String findByIdsSql;
	
	@Value("${series.find_by_category_slug}")
	private String findByCategorySlugSql;
	
	@Value("${series.find_by_country_slug}")
	private String findByCountrySlugSql;
	
	@Value("${series.find_purchases_and_sales_by_series_id}")
	private String findPurchasesAndSalesBySeriesIdSql;
	
	@Value("${series.count_all_series}")
	private String countAllSql;
	
	@Value("${series.count_all_stamps}")
	private String countAllStampsSql;
	
	@Value("${series.count_series_by_id}")
	private String countSeriesByIdSql;
	
	@Value("${series.count_series_added_since}")
	private String countSeriesAddedSinceSql;
	
	@Value("${series.count_series_updated_since}")
	private String countSeriesUpdatedSinceSql;
	
	@Value("${series.find_quantity_by_id}")
	private String findQuantityByIdSql;
	
	@Override
	public Integer add(AddSeriesDbDto series) {
		Map<String, Object> params = new HashMap<>();
		params.put("category_id", series.getCategoryId());
		params.put("country_id", series.getCountryId());
		params.put("quantity", series.getQuantity());
		params.put("perforated", series.getPerforated());
		params.put("release_day", series.getReleaseDay());
		params.put("release_month", series.getReleaseMonth());
		params.put("release_year", series.getReleaseYear());
		params.put("michel_price", series.getMichelPrice());
		params.put("scott_price", series.getScottPrice());
		params.put("yvert_price", series.getYvertPrice());
		params.put("gibbons_price", series.getGibbonsPrice());
		params.put("solovyov_price", series.getSolovyovPrice());
		params.put("zagorski_price", series.getZagorskiPrice());
		params.put("comment", series.getComment());
		params.put("created_at", series.getCreatedAt());
		params.put("created_by", series.getCreatedBy());
		params.put("updated_at", series.getUpdatedAt());
		params.put("updated_by", series.getUpdatedBy());
		
		KeyHolder holder = new GeneratedKeyHolder();
		
		int affected = jdbcTemplate.update(
			createSeriesSql,
			new MapSqlParameterSource(params),
			holder
		);
		
		Validate.validState(
			affected == 1,
			"Unexpected number of affected rows after creation of series: %d",
			affected
		);
		
		return Integer.valueOf(holder.getKey().intValue());
	}
	
	/**
	 * @author Sergey Chechenev
	 */
	@Override
	public void markAsModified(Integer seriesId, Date updatedAt, Integer updatedBy) {
		Map<String, Object> params = new HashMap<>();
		params.put("series_id", seriesId);
		params.put("updated_at", updatedAt);
		params.put("updated_by", updatedBy);
		
		int affected = jdbcTemplate.update(
			markAsModifiedSql,
			params
		);
		
		Validate.validState(
			affected == 1,
			"Unexpected number of affected rows after updating series: %d",
			affected
		);
	}
	
	@Override
	public List<SitemapInfoDto> findAllForSitemap() {
		return jdbcTemplate.query(
			findAllForSitemapSql,
			Collections.emptyMap(),
			RowMappers::forSitemapInfoDto
		);
	}
	
	@Override
	public List<SeriesLinkDto> findLastAdded(int quantity, String lang) {
		Map<String, Object> params = new HashMap<>();
		params.put("quantity", quantity);
		params.put("lang", lang);
		
		return jdbcTemplate.query(findLastAddedSeriesSql, params, RowMappers::forSeriesLinkDto);
	}
	
	@Override
	public SeriesFullInfoDto findByIdAsSeriesFullInfo(Integer seriesId, String lang) {
		Map<String, Object> params = new HashMap<>();
		params.put("series_id", seriesId);
		params.put("lang", lang);
		
		try {
			return jdbcTemplate.queryForObject(
				findFullInfoByIdSql,
				params,
				RowMappers::forSeriesFullInfoDto
			);
		} catch (EmptyResultDataAccessException ignored) {
			return null;
		}
	}
	
	/**
	 * @author Sergey Chechenev
	 */
	@Override
	public List<SeriesInfoDto> findByIdsAsSeriesInfo(List<Integer> seriesIds, String lang) {
		Map<String, Object> params = new HashMap<>();
		params.put("series_ids", seriesIds);
		params.put("lang", lang);

		return jdbcTemplate.query(findByIdsSql, params, RowMappers::forSeriesInfoDto);
	}

	@Override
	public List<SeriesInfoDto> findByCategorySlugAsSeriesInfo(String slug, String lang) {
		Map<String, Object> params = new HashMap<>();
		params.put("slug", slug);
		params.put("lang", lang);
		
		return jdbcTemplate.query(findByCategorySlugSql, params, RowMappers::forSeriesInfoDto);
	}
	
	@Override
	public List<SeriesInfoDto> findByCountrySlugAsSeriesInfo(String slug, String lang) {
		Map<String, Object> params = new HashMap<>();
		params.put("slug", slug);
		params.put("lang", lang);
		
		return jdbcTemplate.query(findByCountrySlugSql, params, RowMappers::forSeriesInfoDto);
	}
	
	/**
	 * @author Sergey Chechenev
	 */
	@Override
	public List<PurchaseAndSaleDto> findPurchasesAndSales(Integer seriesId) {
		return jdbcTemplate.query(
			findPurchasesAndSalesBySeriesIdSql,
			Collections.singletonMap("series_id", seriesId),
			RowMappers::forPurchaseAndSaleDto
		);
	}
	
	@Override
	public long countAll() {
		return jdbcTemplate.queryForObject(countAllSql, Collections.emptyMap(), Long.class);
	}
	
	@Override
	public long countAllStamps() {
		return jdbcTemplate.queryForObject(countAllStampsSql, Collections.emptyMap(), Long.class);
	}
	
	@Override
	public long countSeriesById(Integer seriesId) {
		return jdbcTemplate.queryForObject(
			countSeriesByIdSql,
			Collections.singletonMap("series_id", seriesId),
			Long.class
		);
	}
	
	@Override
	public long countAddedSince(Date date) {
		return jdbcTemplate.queryForObject(
			countSeriesAddedSinceSql,
			Collections.singletonMap("date", date),
			Long.class
		);
	}
	
	@Override
	public long countUpdatedSince(Date date) {
		return jdbcTemplate.queryForObject(
			countSeriesUpdatedSinceSql,
			Collections.singletonMap("date", date),
			Long.class
		);
	}
	
	@Override
	public Integer findQuantityById(Integer seriesId) {
		try {
			return jdbcTemplate.queryForObject(
				findQuantityByIdSql,
				Collections.singletonMap("series_id", seriesId),
				Integer.class
			);
		} catch (EmptyResultDataAccessException ignored) {
			return null;
		}
	}
	
}
