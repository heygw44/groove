package com.groove.notification.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.willAnswer;
import static org.mockito.BDDMockito.willThrow;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

import java.util.List;
import java.util.Optional;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.dao.DataIntegrityViolationException;

import com.groove.fixture.AlbumFixture;
import com.groove.fixture.AlbumWatchFixture;
import com.groove.fixture.ArtistFixture;
import com.groove.fixture.MemberFixture;
import com.groove.global.common.BusinessException;
import com.groove.global.common.ErrorCode;
import com.groove.member.entity.Member;
import com.groove.member.repository.MemberRepository;
import com.groove.notification.dto.AlbumWatchListResponse;
import com.groove.notification.dto.AlbumWatchResponse;
import com.groove.notification.entity.AlbumWatch;
import com.groove.notification.repository.AlbumWatchRepository;
import com.groove.product.entity.Album;
import com.groove.product.entity.Artist;
import com.groove.product.repository.AlbumRepository;

@ExtendWith(MockitoExtension.class)
class AlbumWatchServiceTest {

	private static final Long MEMBER_ID = 1L;
	private static final Long ALBUM_ID = 100L;
	private static final Long ALBUM_WATCH_ID = 1000L;

	@Mock
	AlbumWatchRepository albumWatchRepository;

	@Mock
	MemberRepository memberRepository;

	@Mock
	AlbumRepository albumRepository;

	AlbumWatchService albumWatchService;

	Member member;
	Album album;

	@BeforeEach
	void setUp() {
		albumWatchService = new AlbumWatchService(albumWatchRepository, memberRepository, albumRepository);
		member = MemberFixture.withId(MemberFixture.create(), MEMBER_ID);
		Artist artist = ArtistFixture.withId(1L);
		album = AlbumFixture.withId(AlbumFixture.create(artist), ALBUM_ID);
	}

	@Nested
	@DisplayName("add()")
	class Add {

		@Test
		@DisplayName("정상 요청이면 앨범 구독을 등록한다")
		void addsAlbumWatch() {
			// given
			given(memberRepository.findById(MEMBER_ID)).willReturn(Optional.of(member));
			given(albumRepository.findById(ALBUM_ID)).willReturn(Optional.of(album));
			given(albumWatchRepository.existsByMemberIdAndAlbumId(MEMBER_ID, ALBUM_ID)).willReturn(false);
			willAnswer(invocation -> AlbumWatchFixture.withId(invocation.getArgument(0), ALBUM_WATCH_ID))
					.given(albumWatchRepository).saveAndFlush(any());

			// when
			AlbumWatchResponse response = albumWatchService.add(MEMBER_ID, ALBUM_ID);

			// then
			assertThat(response.albumId()).isEqualTo(ALBUM_ID);
			assertThat(response.albumTitle()).isEqualTo(album.getTitle());
		}

		@Test
		@DisplayName("존재하지 않는 앨범이면 ALBUM_NOT_FOUND 예외를 던진다")
		void throwsWhenAlbumNotFound() {
			// given
			given(memberRepository.findById(MEMBER_ID)).willReturn(Optional.of(member));
			given(albumRepository.findById(ALBUM_ID)).willReturn(Optional.empty());

			// when & then
			assertThatThrownBy(() -> albumWatchService.add(MEMBER_ID, ALBUM_ID))
					.isInstanceOf(BusinessException.class)
					.extracting("errorCode")
					.isEqualTo(ErrorCode.ALBUM_NOT_FOUND);
		}

		@Test
		@DisplayName("이미 구독 중이면 ALBUM_WATCH_ALREADY_EXISTS 예외를 던지고 저장하지 않는다")
		void throwsWhenAlreadyWatching() {
			// given
			given(memberRepository.findById(MEMBER_ID)).willReturn(Optional.of(member));
			given(albumRepository.findById(ALBUM_ID)).willReturn(Optional.of(album));
			given(albumWatchRepository.existsByMemberIdAndAlbumId(MEMBER_ID, ALBUM_ID)).willReturn(true);

			// when & then
			assertThatThrownBy(() -> albumWatchService.add(MEMBER_ID, ALBUM_ID))
					.isInstanceOf(BusinessException.class)
					.extracting("errorCode")
					.isEqualTo(ErrorCode.ALBUM_WATCH_ALREADY_EXISTS);
			verify(albumWatchRepository, never()).saveAndFlush(any());
		}

		@Test
		@DisplayName("동시 등록으로 저장 시점에 유니크 제약을 위반하면 ALBUM_WATCH_ALREADY_EXISTS 예외로 변환한다")
		void throwsWhenSaveViolatesUniqueConstraint() {
			// given
			given(memberRepository.findById(MEMBER_ID)).willReturn(Optional.of(member));
			given(albumRepository.findById(ALBUM_ID)).willReturn(Optional.of(album));
			given(albumWatchRepository.existsByMemberIdAndAlbumId(MEMBER_ID, ALBUM_ID)).willReturn(false);
			willThrow(new DataIntegrityViolationException("duplicate"))
					.given(albumWatchRepository).saveAndFlush(any());

			// when & then
			assertThatThrownBy(() -> albumWatchService.add(MEMBER_ID, ALBUM_ID))
					.isInstanceOf(BusinessException.class)
					.extracting("errorCode")
					.isEqualTo(ErrorCode.ALBUM_WATCH_ALREADY_EXISTS);
		}
	}

	@Nested
	@DisplayName("remove()")
	class Remove {

		@Test
		@DisplayName("구독 중이면 해지한다")
		void removesAlbumWatch() {
			// given
			AlbumWatch albumWatch = AlbumWatchFixture.withId(AlbumWatchFixture.create(member, album),
					ALBUM_WATCH_ID);
			given(albumWatchRepository.findByMemberIdAndAlbumId(MEMBER_ID, ALBUM_ID))
					.willReturn(Optional.of(albumWatch));

			// when
			albumWatchService.remove(MEMBER_ID, ALBUM_ID);

			// then
			verify(albumWatchRepository).delete(albumWatch);
		}

		@Test
		@DisplayName("구독하지 않은 앨범이면 ALBUM_WATCH_NOT_FOUND 예외를 던진다")
		void throwsWhenNotWatching() {
			// given
			given(albumWatchRepository.findByMemberIdAndAlbumId(MEMBER_ID, ALBUM_ID)).willReturn(Optional.empty());

			// when & then
			assertThatThrownBy(() -> albumWatchService.remove(MEMBER_ID, ALBUM_ID))
					.isInstanceOf(BusinessException.class)
					.extracting("errorCode")
					.isEqualTo(ErrorCode.ALBUM_WATCH_NOT_FOUND);
		}
	}

	@Nested
	@DisplayName("getMyWatches()")
	class GetMyWatches {

		@Test
		@DisplayName("내 구독 목록을 등록일 내림차순으로 반환한다")
		void returnsMyWatches() {
			// given
			AlbumWatch albumWatch = AlbumWatchFixture.withId(AlbumWatchFixture.create(member, album),
					ALBUM_WATCH_ID);
			given(albumWatchRepository.findAllByMemberIdOrderByCreatedAtDescIdDesc(MEMBER_ID))
					.willReturn(List.of(albumWatch));

			// when
			AlbumWatchListResponse response = albumWatchService.getMyWatches(MEMBER_ID);

			// then
			assertThat(response.content()).hasSize(1);
			assertThat(response.content().get(0).albumId()).isEqualTo(ALBUM_ID);
		}

		@Test
		@DisplayName("구독한 앨범이 없으면 빈 목록을 반환한다")
		void returnsEmptyListWhenNoWatches() {
			// given
			given(albumWatchRepository.findAllByMemberIdOrderByCreatedAtDescIdDesc(MEMBER_ID))
					.willReturn(List.of());

			// when
			AlbumWatchListResponse response = albumWatchService.getMyWatches(MEMBER_ID);

			// then
			assertThat(response.content()).isEmpty();
		}
	}
}
