package com.groove.notification.service;

import java.util.List;

import org.springframework.dao.CannotAcquireLockException;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.groove.global.common.BusinessException;
import com.groove.global.common.ErrorCode;
import com.groove.member.entity.Member;
import com.groove.member.repository.MemberRepository;
import com.groove.notification.dto.AlbumWatchListResponse;
import com.groove.notification.dto.AlbumWatchResponse;
import com.groove.notification.entity.AlbumWatch;
import com.groove.notification.repository.AlbumWatchRepository;
import com.groove.product.entity.Album;
import com.groove.product.repository.AlbumRepository;

import lombok.RequiredArgsConstructor;

/** 앨범 새 프레싱 알림 구독 등록/해지/조회. */
@Service
@Transactional(readOnly = true)
@RequiredArgsConstructor
public class AlbumWatchService {

	private final AlbumWatchRepository albumWatchRepository;
	private final MemberRepository memberRepository;
	private final AlbumRepository albumRepository;

	@Transactional
	public AlbumWatchResponse add(Long memberId, Long albumId) {
		Member member = findActiveMember(memberId);
		Album album = albumRepository.findById(albumId)
				.orElseThrow(() -> new BusinessException(ErrorCode.ALBUM_NOT_FOUND));
		if (albumWatchRepository.existsByMemberIdAndAlbumId(memberId, albumId)) {
			throw new BusinessException(ErrorCode.ALBUM_WATCH_ALREADY_EXISTS);
		}

		AlbumWatch saved;
		try {
			saved = albumWatchRepository.saveAndFlush(AlbumWatch.create(member, album));
		} catch (DataIntegrityViolationException | CannotAcquireLockException e) {
			// InnoDB 는 같은 유니크 키로 INSERT 가 몰리면 중복키 대신 데드락을 내므로 락 획득 실패도 같이 잡는다.
			throw new BusinessException(ErrorCode.ALBUM_WATCH_ALREADY_EXISTS);
		}

		return AlbumWatchResponse.from(saved);
	}

	@Transactional
	public void remove(Long memberId, Long albumId) {
		AlbumWatch albumWatch = albumWatchRepository.findByMemberIdAndAlbumId(memberId, albumId)
				.orElseThrow(() -> new BusinessException(ErrorCode.ALBUM_WATCH_NOT_FOUND));
		albumWatchRepository.delete(albumWatch);
	}

	public AlbumWatchListResponse getMyWatches(Long memberId) {
		List<AlbumWatchResponse> content = albumWatchRepository
				.findAllByMemberIdOrderByCreatedAtDescIdDesc(memberId).stream()
				.map(AlbumWatchResponse::from)
				.toList();
		return AlbumWatchListResponse.of(content);
	}

	private Member findActiveMember(Long memberId) {
		Member member = memberRepository.findById(memberId)
				.orElseThrow(() -> new BusinessException(ErrorCode.MEMBER_NOT_FOUND));
		member.validateActive();
		return member;
	}
}
