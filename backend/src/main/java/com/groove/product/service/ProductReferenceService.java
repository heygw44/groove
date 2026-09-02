package com.groove.product.service;

import java.util.List;

import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import com.groove.product.dto.ArtistResponse;
import com.groove.product.dto.GenreResponse;
import com.groove.product.dto.LabelResponse;
import com.groove.product.repository.ArtistRepository;
import com.groove.product.repository.GenreRepository;
import com.groove.product.repository.LabelRepository;

import lombok.RequiredArgsConstructor;

/** 상품 필터용 기준 데이터(장르·레이블·아티스트) 조회. */
@Service
@Transactional(readOnly = true)
@RequiredArgsConstructor
public class ProductReferenceService {

	private static final int ARTIST_LIMIT = 20;

	private final GenreRepository genreRepository;
	private final LabelRepository labelRepository;
	private final ArtistRepository artistRepository;

	public List<GenreResponse> getGenres() {
		return genreRepository.findAllByOrderByNameAsc().stream()
				.map(GenreResponse::from)
				.toList();
	}

	public List<LabelResponse> getLabels() {
		return labelRepository.findAllByOrderByNameAsc().stream()
				.map(LabelResponse::from)
				.toList();
	}

	public List<ArtistResponse> searchArtists(String keyword) {
		String normalizedKeyword = StringUtils.hasText(keyword) ? keyword.trim() : null;
		return artistRepository.searchByKeyword(normalizedKeyword, PageRequest.of(0, ARTIST_LIMIT)).stream()
				.map(ArtistResponse::from)
				.toList();
	}
}
