package com.groove.catalog.scheduler;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.mockito.AdditionalAnswers.delegatesTo;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.LocalDateTime;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.ApplicationEventPublisher;

import com.groove.catalog.client.PressingLookupClient;
import com.groove.catalog.config.CatalogFreshnessProperties;
import com.groove.catalog.config.CatalogResyncProperties;
import com.groove.catalog.mapper.DiscogsResyncMapper;
import com.groove.catalog.service.DiscogsResyncLock;
import com.groove.catalog.service.DiscogsResyncService;
import com.groove.catalog.support.FakePressingLookupClient;
import com.groove.catalog.support.ProductCatalogChangedEventRecorder;
import com.groove.fixture.AlbumFixture;
import com.groove.fixture.ArtistFixture;
import com.groove.fixture.DiscogsFixture;
import com.groove.global.alert.AlertNotifier;
import com.groove.global.common.BusinessException;
import com.groove.global.common.ErrorCode;
import com.groove.global.lifecycle.ShutdownSignal;
import com.groove.product.entity.Album;
import com.groove.product.entity.Artist;
import com.groove.product.entity.EditionType;
import com.groove.product.entity.Product;
import com.groove.product.repository.AlbumRepository;
import com.groove.product.repository.ArtistRepository;
import com.groove.product.repository.ProductRepository;
import com.groove.support.IntegrationTestSupport;

class DiscogsResyncSchedulerTest extends IntegrationTestSupport {

	private static final BigDecimal PRICE = new BigDecimal("30000");
	// 후보 정렬이 discogs_synced_at 오름차순이라, 공유 테스트 DB 에 다른 테스트가 커밋해 둔 stale 상품보다
	// 확실히 앞에 서도록 극단적으로 오래된 값을 쓴다. 예산(회당 3건) 안에 이 테스트 상품이 들어가야 한다.
	private static final int STALE_YEARS = 50;

	@Autowired
	private DiscogsResyncScheduler discogsResyncScheduler;

	@Autowired
	private PressingLookupClient pressingLookupClient;

	@Autowired
	private ProductRepository productRepository;

	@Autowired
	private AlbumRepository albumRepository;

	@Autowired
	private ArtistRepository artistRepository;

	@Autowired
	private ProductCatalogChangedEventRecorder eventRecorder;

	@Autowired
	private Clock clock;

	@Autowired
	private DiscogsResyncMapper discogsResyncMapper;

	@Autowired
	private DiscogsResyncService discogsResyncService;

	@Autowired
	private DiscogsResyncLock discogsResyncLock;

	@Autowired
	private CatalogResyncProperties resyncProperties;

	@Autowired
	private CatalogFreshnessProperties freshnessProperties;

	@Autowired
	private ApplicationEventPublisher eventPublisher;

	@Autowired
	private ShutdownSignal shutdownSignal;

	private FakePressingLookupClient fake;
	private final List<Long> createdProductIds = new ArrayList<>();

	@BeforeEach
	void setUp() {
		fake = (FakePressingLookupClient) pressingLookupClient;
		fake.reset();
		createdProductIds.clear();
	}

	// 여기서 만든 상품만 정리해 다음 테스트의 후보 조회(전체 상품 대상 쿼리)가 이전 stale 상품에 걸리지 않게 한다.
	@AfterEach
	void tearDown() {
		productRepository.deleteAllByIdInBatch(createdProductIds);
	}

	// DB 왕복 후 isEqualTo 로 비교하는 값이라 초 미만을 잘라 둔다. Linux 러너의 Clock 은 나노초까지
	// 주는데 datetime(6) 은 마이크로초까지만 담아, 그대로 두면 CI 에서만 어긋난다.
	private LocalDateTime staleInstant() {
		return LocalDateTime.now(clock).minusYears(STALE_YEARS).truncatedTo(ChronoUnit.SECONDS);
	}

	private Product persistStaleProduct(String title, long discogsReleaseId, LocalDateTime syncedAt) {
		Artist artist = artistRepository.save(ArtistFixture.create("DRS " + title));
		Album album = albumRepository.save(AlbumFixture.create(artist, "DRS Album " + title));
		Product product = Product.createImported(album, title, artist, null, "Germany", 1959, "CS 8163",
				"5012394144777", EditionType.STANDARD, PRICE, discogsReleaseId, syncedAt);
		product.resume();
		product = productRepository.save(product);
		createdProductIds.add(product.getId());
		return product;
	}

	// 컨텍스트를 새로 띄우면 공유 DB 가 재생성되므로 빈을 갈아끼우지 않고 협력 객체만 바꾼 스케줄러를 직접 만든다.
	private DiscogsResyncScheduler schedulerWith(DiscogsResyncService service, PressingLookupClient client) {
		return new DiscogsResyncScheduler(discogsResyncMapper, service, discogsResyncLock, client, resyncProperties,
				freshnessProperties, eventPublisher, shutdownSignal, clock, mock(AlertNotifier.class));
	}

	@Nested
	@DisplayName("resyncPriority()")
	class ResyncPriority {

		@Test
		@DisplayName("예산 상한을 넘겨 호출하지 않는다")
		void doesNotExceedBudgetPerRun() {
			// given: view_count·discogs_synced_at 이 전부 같아 id 오름차순(입력 순)으로 정렬된다
			LocalDateTime veryStale = staleInstant();
			List<Product> products = new ArrayList<>();
			for (int i = 0; i < 5; i++) {
				long releaseId = 91_000_001L + i;
				Product product = persistStaleProduct("Budget " + i, releaseId, veryStale);
				fake.addRelease(DiscogsFixture.releaseResponse(releaseId, 21247L, "Miles Davis", "Columbia",
						"CS 8163", List.of(), "5012394144777", List.of("Jazz"), List.of(), 1959));
				products.add(product);
			}

			// when
			discogsResyncScheduler.resyncPriority();

			// then
			// 후보 조회는 전역이라 어떤 상품이 뽑혔는지는 공유 DB 상태에 따라 달라진다. 예산을 지켰는지만 잰다.
			assertThat(fake.totalReleaseCalls()).isEqualTo(3);
			long processed = products.stream()
					.map(p -> productRepository.findById(p.getId()).orElseThrow())
					.filter(p -> p.getDiscogsSyncedAt() != null && p.getDiscogsSyncedAt().isAfter(veryStale))
					.count();
			assertThat(processed).isLessThanOrEqualTo(3);
		}

		@Test
		@DisplayName("건별 실패가 나머지 처리를 막지 않는다")
		void continuesProcessingAfterIndividualFailure() {
			// given
			LocalDateTime stale = staleInstant();
			long failingReleaseId = 92_000_001L;
			long okReleaseId = 92_000_002L;
			Product failingProduct = persistStaleProduct("Failing", failingReleaseId, stale);
			Product okProduct = persistStaleProduct("Ok", okReleaseId, stale);
			fake.failTransiently(failingReleaseId, -1);
			fake.addRelease(DiscogsFixture.releaseResponse(okReleaseId, 21247L, "Miles Davis", "Columbia", "CS 8163",
					List.of(), "5012394144777", List.of("Jazz"), List.of(), 1959));

			// when & then
			assertThatCode(() -> discogsResyncScheduler.resyncPriority()).doesNotThrowAnyException();

			Product reloadedOk = productRepository.findById(okProduct.getId()).orElseThrow();
			assertThat(reloadedOk.getDiscogsSyncedAt()).isAfter(stale);
			assertThat(reloadedOk.getDiscogsResyncFailedAt()).isNull();

			Optional<Product> reloadedFailing = productRepository.findById(failingProduct.getId());
			assertThat(reloadedFailing).isPresent();
			assertThat(reloadedFailing.get().getDiscogsSyncedAt()).isEqualTo(stale);
			assertThat(reloadedFailing.get().getDiscogsResyncFailedAt()).isNotNull();
		}

		@Test
		@DisplayName("실패 시각이 남은 상품은 쿨다운 동안 다시 호출하지 않는다")
		void skipsFailedProductDuringCooldown() {
			// given
			long failingReleaseId = 92_100_001L;
			persistStaleProduct("Cooldown", failingReleaseId, staleInstant());
			fake.failTransiently(failingReleaseId, -1);
			discogsResyncScheduler.resyncPriority();

			// when
			discogsResyncScheduler.resyncPriority();

			// then
			assertThat(fake.releaseCalls(failingReleaseId)).isEqualTo(1);
		}

		@Test
		@DisplayName("레이트리밋 실패는 행 문제가 아니라 실패 시각을 남기지 않는다")
		void doesNotRecordFailureWhenRateLimited() {
			// given
			Product product = persistStaleProduct("RateLimited", 92_200_001L, staleInstant());
			PressingLookupClient rateLimitedClient = mock(PressingLookupClient.class);
			given(rateLimitedClient.getRelease(anyLong()))
					.willThrow(new BusinessException(ErrorCode.CATALOG_RATE_LIMITED));
			DiscogsResyncService service = mock(DiscogsResyncService.class, delegatesTo(discogsResyncService));

			// when
			schedulerWith(service, rateLimitedClient).resyncPriority();

			// then
			verify(service, never()).markResyncFailed(any(), any());
			Product reloaded = productRepository.findById(product.getId()).orElseThrow();
			assertThat(reloaded.getDiscogsResyncFailedAt()).isNull();
		}

		@Test
		@DisplayName("실패 기록이 예외를 던져도 나머지 후보를 계속 처리한다")
		void continuesProcessingWhenFailureRecordingThrows() {
			// given
			LocalDateTime stale = staleInstant();
			long failingReleaseId = 92_300_001L;
			long okReleaseId = 92_300_002L;
			Product failingProduct = persistStaleProduct("RecordFails", failingReleaseId, stale);
			Product okProduct = persistStaleProduct("RecordOk", okReleaseId, stale);
			fake.failTransiently(failingReleaseId, -1);
			fake.addRelease(DiscogsFixture.releaseResponse(okReleaseId, 21247L, "Miles Davis", "Columbia", "CS 8163",
					List.of(), "5012394144777", List.of("Jazz"), List.of(), 1959));
			DiscogsResyncService service = mock(DiscogsResyncService.class, delegatesTo(discogsResyncService));
			doThrow(new IllegalStateException("boom")).when(service)
					.markResyncFailed(eq(failingProduct.getId()), any());

			// when & then
			assertThatCode(() -> schedulerWith(service, pressingLookupClient).resyncPriority())
					.doesNotThrowAnyException();

			verify(service).markResyncFailed(eq(failingProduct.getId()), any());
			Product reloadedOk = productRepository.findById(okProduct.getId()).orElseThrow();
			assertThat(reloadedOk.getDiscogsSyncedAt()).isAfter(stale);
		}

		@Test
		@DisplayName("404 참조 해제가 예외를 던져도 나머지 후보를 계속 처리한다")
		void continuesProcessingWhenMarkReleaseNotFoundThrows() {
			// given
			LocalDateTime stale = staleInstant();
			long notFoundReleaseId = 92_400_001L;
			long okReleaseId = 92_400_002L;
			Product notFoundProduct = persistStaleProduct("ClearFails", notFoundReleaseId, stale);
			Product okProduct = persistStaleProduct("ClearOk", okReleaseId, stale);
			fake.markNotFound(notFoundReleaseId);
			fake.addRelease(DiscogsFixture.releaseResponse(okReleaseId, 21247L, "Miles Davis", "Columbia", "CS 8163",
					List.of(), "5012394144777", List.of("Jazz"), List.of(), 1959));
			DiscogsResyncService service = mock(DiscogsResyncService.class, delegatesTo(discogsResyncService));
			doThrow(new IllegalStateException("boom")).when(service)
					.markReleaseNotFound(eq(notFoundProduct.getId()), any());

			// when & then
			assertThatCode(() -> schedulerWith(service, pressingLookupClient).resyncPriority())
					.doesNotThrowAnyException();

			Product reloadedOk = productRepository.findById(okProduct.getId()).orElseThrow();
			assertThat(reloadedOk.getDiscogsSyncedAt()).isAfter(stale);
		}

		@Test
		@DisplayName("404 상품은 참조를 끊고 후보에서 영구히 빠진다")
		void removesNotFoundProductFromCandidatesPermanently() {
			// given
			LocalDateTime stale = staleInstant();
			long releaseId = 93_000_001L;
			Product product = persistStaleProduct("NotFound", releaseId, stale);
			fake.markNotFound(releaseId);

			// when
			discogsResyncScheduler.resyncPriority();

			// then
			Product reloaded = productRepository.findById(product.getId()).orElseThrow();
			assertThat(reloaded.getDiscogsReleaseId()).isNull();
			assertThat(reloaded.getDiscogsSyncedAt()).isNull();

			// when: 다시 실행해도 이미 후보에서 빠졌으니 다시 호출하지 않는다
			discogsResyncScheduler.resyncPriority();

			// then
			assertThat(fake.releaseCalls(releaseId)).isEqualTo(1);
		}

		@Test
		@DisplayName("변경 건수가 있으면 ProductCatalogChangedEvent 를 발행한다")
		void publishesEventWhenFieldsChanged() {
			// given
			LocalDateTime stale = staleInstant();
			long releaseId = 94_000_001L;
			persistStaleProduct("Changed", releaseId, stale);
			fake.addRelease(DiscogsFixture.releaseResponse(releaseId, 21247L, "Miles Davis", "Columbia",
					"CS 8163-CHANGED", List.of(), "5012394144777", List.of("Jazz"), List.of(), 1959));
			int before = eventRecorder.count();

			// when
			discogsResyncScheduler.resyncPriority();

			// then
			assertThat(eventRecorder.count() - before).isEqualTo(1);
		}

		@Test
		@DisplayName("변경 건수가 없으면 ProductCatalogChangedEvent 를 발행하지 않는다")
		void doesNotPublishEventWhenNothingChanged() {
			// given
			LocalDateTime stale = staleInstant();
			long releaseId = 95_000_001L;
			persistStaleProduct("Unchanged", releaseId, stale);
			fake.addRelease(DiscogsFixture.releaseResponse(releaseId, 21247L, "Miles Davis", "Columbia", "CS 8163",
					List.of(), "5012394144777", List.of("Jazz"), List.of(), 1959));
			int before = eventRecorder.count();

			// when
			discogsResyncScheduler.resyncPriority();

			// then
			assertThat(eventRecorder.count() - before).isEqualTo(0);
		}
	}
}
