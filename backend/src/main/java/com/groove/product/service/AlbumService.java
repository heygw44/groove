package com.groove.product.service;

import java.util.List;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import com.groove.global.common.BusinessException;
import com.groove.global.common.ErrorCode;
import com.groove.global.common.PageResponse;
import com.groove.product.dto.AdminAlbumSummaryResponse;
import com.groove.product.dto.AlbumDetailResponse;
import com.groove.product.dto.ProductSummaryResponse;
import com.groove.product.entity.Album;
import com.groove.product.mapper.ProductSearchMapper;
import com.groove.product.repository.AlbumRepository;

import lombok.RequiredArgsConstructor;

/** 앨범(작품 단위) 상세 + 프레싱 목록 조회. */
@Service
@Transactional(readOnly = true)
@RequiredArgsConstructor
public class AlbumService {

	private final AlbumRepository albumRepository;
	private final ProductSearchMapper productSearchMapper;

	public AlbumDetailResponse getDetail(Long id) {
		Album album = albumRepository.findWithArtistById(id)
				.orElseThrow(() -> new BusinessException(ErrorCode.ALBUM_NOT_FOUND));
		List<ProductSummaryResponse> pressings = productSearchMapper.findAlbumPressings(id);
		return AlbumDetailResponse.from(album, pressings);
	}

	public PageResponse<AdminAlbumSummaryResponse> getAdminList(String keyword, Pageable pageable) {
		String normalizedKeyword = StringUtils.hasText(keyword) ? keyword.trim() : null;
		Page<AdminAlbumSummaryResponse> page = albumRepository.searchByKeyword(normalizedKeyword, pageable)
				.map(AdminAlbumSummaryResponse::from);
		return PageResponse.from(page);
	}
}
