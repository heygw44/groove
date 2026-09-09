package com.groove.catalog.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.List;
import java.util.Optional;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

import com.groove.catalog.client.dto.DiscogsReleaseResponse;
import com.groove.catalog.dto.DiscogsResyncOutcome;
import com.groove.fixture.AlbumFixture;
import com.groove.fixture.ArtistFixture;
import com.groove.fixture.DiscogsFixture;
import com.groove.fixture.ProductFixture;
import com.groove.global.common.BusinessException;
import com.groove.global.common.ErrorCode;
import com.groove.product.entity.Album;
import com.groove.product.entity.Artist;
import com.groove.product.entity.EditionType;
import com.groove.product.entity.Product;
import com.groove.product.repository.ProductRepository;

@ExtendWith(MockitoExtension.class)
class DiscogsResyncServiceTest {

	private static final ZoneId ZONE = ZoneId.of("Asia/Seoul");
	private static final Long PRODUCT_ID = 1L;
	private static final Long RELEASE_ID = 249504L;

	@Mock
	private ProductRepository productRepository;

	private DiscogsResyncService discogsResyncService;

	private Clock clock;
	private Artist artist;
	private Album album;
	private Product product;

	@BeforeEach
	void setUp() {
		clock = Clock.fixed(Instant.parse("2026-09-09T00:00:00Z"), ZONE);
		discogsResyncService = new DiscogsResyncService(productRepository, new DiscogsReleaseMapper(), clock);

		artist = ArtistFixture.create();
		album = AlbumFixture.withId(AlbumFixture.create(artist), 10L);
		product = ProductFixture.createPressing(album, artist, "Kind of Blue", new BigDecimal("30000"), "Germany",
				1959, "CS 8163", "5012394144777", EditionType.STANDARD);
		ProductFixture.withId(product, PRODUCT_ID);
		ReflectionTestUtils.setField(product, "discogsReleaseId", RELEASE_ID);
		ReflectionTestUtils.setField(product, "discogsSyncedAt", LocalDateTime.now(clock).minusDays(1));
	}

	@Nested
	@DisplayName("apply()")
	class Apply {

		@Test
		@DisplayName("바뀐 필드만 갱신하고 title·price·description 은 보존한다")
		void updatesOnlyChangedFieldsAndPreservesAdminManagedFields() {
			// given
			given(productRepository.findById(PRODUCT_ID)).willReturn(Optional.of(product));
			DiscogsReleaseResponse release = DiscogsFixture.releaseResponse(RELEASE_ID, 21247L, "Miles Davis",
					"Columbia", "CS 8163-X", List.of("Vinyl", "Reissue"), "5012394144777", List.of("Jazz"),
					List.of(), 1960);
			String originalTitle = product.getTitle();
			var originalPrice = product.getPrice();

			// when
			DiscogsResyncOutcome outcome = discogsResyncService.apply(PRODUCT_ID, RELEASE_ID, release);

			// then
			assertThat(outcome.changed()).isTrue();
			assertThat(product.getCatalogNo()).isEqualTo("CS 8163-X");
			assertThat(product.getPressingYear()).isEqualTo(1960);
			assertThat(product.getEditionType()).isEqualTo(EditionType.REISSUE);
			assertThat(product.getTitle()).isEqualTo(originalTitle);
			assertThat(product.getPrice()).isEqualByComparingTo(originalPrice);
			assertThat(product.getDiscogsSyncedAt()).isEqualTo(LocalDateTime.now(clock));
		}

		@Test
		@DisplayName("필드가 하나도 안 바뀌어도 discogsSyncedAt 은 갱신한다")
		void refreshesSyncedAtEvenWhenNothingChanged() {
			// given
			given(productRepository.findById(PRODUCT_ID)).willReturn(Optional.of(product));
			DiscogsReleaseResponse release = DiscogsFixture.releaseResponse(RELEASE_ID, 21247L, "Miles Davis",
					"Columbia", "CS 8163", List.of(), "5012394144777", List.of("Jazz"), List.of(), 1959);

			// when
			DiscogsResyncOutcome outcome = discogsResyncService.apply(PRODUCT_ID, RELEASE_ID, release);

			// then
			assertThat(outcome.changed()).isFalse();
			assertThat(product.getDiscogsSyncedAt()).isEqualTo(LocalDateTime.now(clock));
		}

		@Test
		@DisplayName("릴리즈 id 가 요청과 다르면(병합) 참조 id 를 새 id 로 갱신한다")
		void updatesReleaseIdOnRedirect() {
			// given
			long newReleaseId = 999999L;
			given(productRepository.findById(PRODUCT_ID)).willReturn(Optional.of(product));
			given(productRepository.existsByDiscogsReleaseId(newReleaseId)).willReturn(false);
			DiscogsReleaseResponse release = DiscogsFixture.releaseResponse(newReleaseId, 21247L, "Miles Davis",
					"Columbia", "CS 8163", List.of(), "5012394144777", List.of("Jazz"), List.of(), 1959);

			// when
			discogsResyncService.apply(PRODUCT_ID, RELEASE_ID, release);

			// then
			assertThat(product.getDiscogsReleaseId()).isEqualTo(newReleaseId);
		}

		@Test
		@DisplayName("병합 대상 id 를 다른 상품이 이미 갖고 있으면 참조 id 를 갱신하지 않는다")
		void skipsRedirectWhenNewIdAlreadyTaken() {
			// given
			long newReleaseId = 999999L;
			given(productRepository.findById(PRODUCT_ID)).willReturn(Optional.of(product));
			given(productRepository.existsByDiscogsReleaseId(newReleaseId)).willReturn(true);
			DiscogsReleaseResponse release = DiscogsFixture.releaseResponse(newReleaseId, 21247L, "Miles Davis",
					"Columbia", "CS 8163", List.of(), "5012394144777", List.of("Jazz"), List.of(), 1959);

			// when
			discogsResyncService.apply(PRODUCT_ID, RELEASE_ID, release);

			// then
			assertThat(product.getDiscogsReleaseId()).isEqualTo(RELEASE_ID);
		}

		@Test
		@DisplayName("마스터가 앨범과 다르면 필드는 갱신하되 앨범 소속은 바꾸지 않는다")
		void keepsAlbumLinkageWhenMasterMismatches() {
			// given
			album.linkDiscogsMaster(21247L);
			given(productRepository.findById(PRODUCT_ID)).willReturn(Optional.of(product));
			DiscogsReleaseResponse release = DiscogsFixture.releaseResponse(RELEASE_ID, 555555L, "Miles Davis",
					"Columbia", "CS 8163-X", List.of(), "5012394144777", List.of("Jazz"), List.of(), 1960);

			// when
			DiscogsResyncOutcome outcome = discogsResyncService.apply(PRODUCT_ID, RELEASE_ID, release);

			// then
			assertThat(outcome.changed()).isTrue();
			assertThat(album.getDiscogsMasterId()).isEqualTo(21247L);
		}

		@Test
		@DisplayName("상품이 없으면 PRODUCT_NOT_FOUND 예외를 던진다")
		void throwsWhenProductNotFound() {
			// given
			given(productRepository.findById(PRODUCT_ID)).willReturn(Optional.empty());
			DiscogsReleaseResponse release = DiscogsFixture.releaseResponse(RELEASE_ID, 21247L, "Miles Davis",
					"Columbia", "CS 8163", List.of(), "5012394144777", List.of("Jazz"), List.of(), 1959);

			// when & then
			assertThatThrownBy(() -> discogsResyncService.apply(PRODUCT_ID, RELEASE_ID, release))
					.isInstanceOf(BusinessException.class)
					.extracting("errorCode")
					.isEqualTo(ErrorCode.PRODUCT_NOT_FOUND);
		}
	}

	@Nested
	@DisplayName("markReleaseNotFound()")
	class MarkReleaseNotFound {

		@Test
		@DisplayName("discogsReleaseId 와 discogsSyncedAt 을 null 로 만든다")
		void clearsReleaseReference() {
			// given
			given(productRepository.findById(PRODUCT_ID)).willReturn(Optional.of(product));

			// when
			discogsResyncService.markReleaseNotFound(PRODUCT_ID, RELEASE_ID);

			// then
			assertThat(product.getDiscogsReleaseId()).isNull();
			assertThat(product.getDiscogsSyncedAt()).isNull();
			verify(productRepository, never()).existsByDiscogsReleaseId(anyLong());
		}
	}
}
