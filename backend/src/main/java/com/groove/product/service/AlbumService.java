package com.groove.product.service;

import java.util.List;

import org.springframework.data.domain.Page;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import com.groove.global.common.BusinessException;
import com.groove.global.common.ErrorCode;
import com.groove.global.common.PageResponse;
import com.groove.global.util.LikeEscaper;
import com.groove.notification.repository.AlbumWatchRepository;
import com.groove.product.dto.AdminAlbumSearchRequest;
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
	private final AlbumWatchRepository albumWatchRepository;

	public AlbumDetailResponse getDetail(Long id, Long memberId) {
		Album album = albumRepository.findWithArtistById(id)
				.orElseThrow(() -> new BusinessException(ErrorCode.ALBUM_NOT_FOUND));
		List<ProductSummaryResponse> pressings = productSearchMapper.findAlbumPressings(id);
		Boolean watched = memberId == null
				? null
				: albumWatchRepository.existsByMemberIdAndAlbumId(memberId, id);
		return AlbumDetailResponse.from(album, pressings, watched);
	}

	public PageResponse<AdminAlbumSummaryResponse> getAdminList(AdminAlbumSearchRequest request) {
		String keyword = request.keyword();
		String normalizedKeyword = StringUtils.hasText(keyword) ? LikeEscaper.escape(keyword.trim()) : null;
		Page<AdminAlbumSummaryResponse> page = albumRepository.searchByKeyword(normalizedKeyword,
				request.toPageable())
				.map(AdminAlbumSummaryResponse::from);
		return PageResponse.from(page);
	}
}
