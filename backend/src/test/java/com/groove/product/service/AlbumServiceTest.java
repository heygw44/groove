package com.groove.product.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;

import com.groove.fixture.AlbumFixture;
import com.groove.fixture.ArtistFixture;
import com.groove.global.common.BusinessException;
import com.groove.global.common.ErrorCode;
import com.groove.global.common.PageResponse;
import com.groove.notification.repository.AlbumWatchRepository;
import com.groove.product.dto.AdminAlbumSummaryResponse;
import com.groove.product.dto.AlbumDetailResponse;
import com.groove.product.dto.ProductSummaryResponse;
import com.groove.product.entity.Album;
import com.groove.product.entity.Artist;
import com.groove.product.entity.EditionType;
import com.groove.product.entity.ProductStatus;
import com.groove.product.mapper.ProductSearchMapper;
import com.groove.product.repository.AlbumRepository;

@ExtendWith(MockitoExtension.class)
class AlbumServiceTest {

	@Mock
	private AlbumRepository albumRepository;

	@Mock
	private ProductSearchMapper productSearchMapper;

	@Mock
	private AlbumWatchRepository albumWatchRepository;

	private AlbumService albumService;

	@BeforeEach
	void setUp() {
		albumService = new AlbumService(albumRepository, productSearchMapper, albumWatchRepository);
	}

	@Nested
	@DisplayName("getDetail()")
	class GetDetail {

		@Test
		@DisplayName("존재하면 앨범 정보와 프레싱 목록을 반환한다")
		void returnsAlbumWithPressings() {
			// given
			Artist artist = ArtistFixture.withId(ArtistFixture.create("Miles Davis"), 1L);
			Album album = AlbumFixture.withId(AlbumFixture.create(artist, "Kind Of Blue"), 5L);
			ProductSummaryResponse pressing = new ProductSummaryResponse(10L, "Kind of Blue", "Miles Davis",
					"Columbia", new BigDecimal("42000"), "Standard Black", "180g", ProductStatus.ON_SALE, null, null,
					0, null, "US", 1959, EditionType.ORIGINAL, 5L, 0);
			given(albumRepository.findWithArtistById(5L)).willReturn(Optional.of(album));
			given(productSearchMapper.findAlbumPressings(5L)).willReturn(List.of(pressing));

			// when
			AlbumDetailResponse response = albumService.getDetail(5L, null);

			// then
			assertThat(response.id()).isEqualTo(5L);
			assertThat(response.title()).isEqualTo("Kind Of Blue");
			assertThat(response.artist().name()).isEqualTo("Miles Davis");
			assertThat(response.pressings()).containsExactly(pressing);
		}

		@Test
		@DisplayName("존재하지 않으면 ALBUM_NOT_FOUND 예외를 던진다")
		void throwsWhenNotFound() {
			// given
			given(albumRepository.findWithArtistById(99L)).willReturn(Optional.empty());

			// when & then
			assertThatThrownBy(() -> albumService.getDetail(99L, null))
					.isInstanceOf(BusinessException.class)
					.extracting("errorCode")
					.isEqualTo(ErrorCode.ALBUM_NOT_FOUND);
		}

		@Test
		@DisplayName("구독한 앨범이면 watched 가 true 다")
		void returnsWatchedTrueWhenSubscribed() {
			// given
			Artist artist = ArtistFixture.withId(ArtistFixture.create("Miles Davis"), 1L);
			Album album = AlbumFixture.withId(AlbumFixture.create(artist, "Kind Of Blue"), 5L);
			given(albumRepository.findWithArtistById(5L)).willReturn(Optional.of(album));
			given(productSearchMapper.findAlbumPressings(5L)).willReturn(List.of());
			given(albumWatchRepository.existsByMemberIdAndAlbumId(1L, 5L)).willReturn(true);

			// when
			AlbumDetailResponse response = albumService.getDetail(5L, 1L);

			// then
			assertThat(response.watched()).isTrue();
		}

		@Test
		@DisplayName("구독하지 않은 앨범이면 watched 가 false 다")
		void returnsWatchedFalseWhenNotSubscribed() {
			// given
			Artist artist = ArtistFixture.withId(ArtistFixture.create("Miles Davis"), 1L);
			Album album = AlbumFixture.withId(AlbumFixture.create(artist, "Kind Of Blue"), 5L);
			given(albumRepository.findWithArtistById(5L)).willReturn(Optional.of(album));
			given(productSearchMapper.findAlbumPressings(5L)).willReturn(List.of());
			given(albumWatchRepository.existsByMemberIdAndAlbumId(1L, 5L)).willReturn(false);

			// when
			AlbumDetailResponse response = albumService.getDetail(5L, 1L);

			// then
			assertThat(response.watched()).isFalse();
		}

		@Test
		@DisplayName("비로그인이면 watched 가 null 이고 구독 여부를 조회하지 않는다")
		void returnsWatchedNullWhenGuest() {
			// given
			Artist artist = ArtistFixture.withId(ArtistFixture.create("Miles Davis"), 1L);
			Album album = AlbumFixture.withId(AlbumFixture.create(artist, "Kind Of Blue"), 5L);
			given(albumRepository.findWithArtistById(5L)).willReturn(Optional.of(album));
			given(productSearchMapper.findAlbumPressings(5L)).willReturn(List.of());

			// when
			AlbumDetailResponse response = albumService.getDetail(5L, null);

			// then
			assertThat(response.watched()).isNull();
			verify(albumWatchRepository, never()).existsByMemberIdAndAlbumId(any(), any());
		}
	}

	@Nested
	@DisplayName("getAdminList()")
	class GetAdminList {

		@Test
		@DisplayName("keyword 가 있으면 앞뒤 공백을 제거해 리포지토리에 전달한다")
		void trimsKeywordBeforeSearching() {
			// given
			Artist artist = ArtistFixture.withId(ArtistFixture.create("Miles Davis"), 1L);
			Album album = AlbumFixture.withId(AlbumFixture.create(artist, "Kind Of Blue"), 5L);
			PageRequest pageable = PageRequest.of(0, 20);
			given(albumRepository.searchByKeyword(eq("Kind"), eq(pageable)))
					.willReturn(new PageImpl<>(List.of(album), pageable, 1));

			// when
			PageResponse<AdminAlbumSummaryResponse> response = albumService.getAdminList("  Kind  ", pageable);

			// then
			assertThat(response.content()).hasSize(1);
			assertThat(response.content().get(0).id()).isEqualTo(5L);
			assertThat(response.content().get(0).title()).isEqualTo("Kind Of Blue");
			assertThat(response.content().get(0).artistName()).isEqualTo("Miles Davis");
			verify(albumRepository).searchByKeyword(eq("Kind"), eq(pageable));
		}

		@Test
		@DisplayName("keyword 가 공백뿐이면 null 로 전달해 전체를 조회한다")
		void passesNullWhenKeywordIsBlank() {
			// given
			PageRequest pageable = PageRequest.of(0, 20);
			given(albumRepository.searchByKeyword(isNull(), eq(pageable)))
					.willReturn(new PageImpl<>(List.of(), pageable, 0));

			// when
			PageResponse<AdminAlbumSummaryResponse> response = albumService.getAdminList("   ", pageable);

			// then
			assertThat(response.content()).isEmpty();
			verify(albumRepository).searchByKeyword(isNull(), eq(pageable));
		}
	}
}
