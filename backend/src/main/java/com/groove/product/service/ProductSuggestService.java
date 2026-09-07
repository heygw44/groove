package com.groove.product.service;

import java.util.List;

import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.groove.product.dto.ArtistResponse;
import com.groove.product.dto.ProductSuggestionResponse;
import com.groove.product.mapper.ProductSearchMapper;
import com.groove.product.repository.ArtistRepository;

import lombok.RequiredArgsConstructor;

/** 헤더 검색창 자동완성. 상품·아티스트 제안을 한 번의 요청으로 묶어 내려준다. */
@Service
@Transactional(readOnly = true)
@RequiredArgsConstructor
public class ProductSuggestService {

	private static final int PRODUCT_LIMIT = 5;
	private static final int ARTIST_LIMIT = 3;

	private final ProductSearchMapper productSearchMapper;
	private final ArtistRepository artistRepository;

	public ProductSuggestionResponse suggest(String keyword) {
		String normalizedKeyword = keyword.trim();
		List<ProductSuggestionResponse.Item> products = productSearchMapper.suggestProducts(normalizedKeyword,
				PRODUCT_LIMIT);
		List<ArtistResponse> artists = artistRepository
				.searchByKeyword(normalizedKeyword, PageRequest.of(0, ARTIST_LIMIT)).stream()
				.map(ArtistResponse::from)
				.toList();
		return new ProductSuggestionResponse(products, artists);
	}
}
