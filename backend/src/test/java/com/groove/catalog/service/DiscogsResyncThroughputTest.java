package com.groove.catalog.service;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.IOException;
import java.math.BigDecimal;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

import com.groove.catalog.client.PressingLookupClient;
import com.groove.catalog.client.dto.DiscogsReleaseResponse;
import com.groove.catalog.support.FakePressingLookupClient;
import com.groove.fixture.AlbumFixture;
import com.groove.fixture.ArtistFixture;
import com.groove.fixture.DiscogsFixture;
import com.groove.product.entity.Album;
import com.groove.product.entity.Artist;
import com.groove.product.entity.EditionType;
import com.groove.product.entity.Product;
import com.groove.product.repository.AlbumRepository;
import com.groove.product.repository.ArtistRepository;
import com.groove.product.repository.ProductRepository;
import com.groove.support.IntegrationTestSupport;

/**
 * "10,800건 = 6시간" 산술의 뒷항(건별 로컬 오버헤드)이 앞항(레이트리밋이 정의하는 상수) 대비
 * 무시할 수 있는지를 잰다. 후보 선정 쿼리 자체의 규모별 실행계획은 DB 문제라 이 테스트의 관심사가
 * 아니다(측정은 {@code backend/scripts/perf/index-explain.sh} 의 D1/D2 케이스가 맡는다) - 여기서는
 * {@link PressingLookupClient#getRelease}(네트워크는 {@link FakePressingLookupClient} 로 대체해 0에 가깝다)
 * 와 {@link DiscogsResyncService#apply} 한 건을 1만 번 반복했을 때 순수 DB 읽기·쓰기 오버헤드만 잰다.
 *
 * <p>일반 빌드에서는 제외되고 {@code ./gradlew discogsResyncThroughput} 으로만 돈다
 * ({@code RecommendPrecisionTest}/{@code precisionAt10} 과 같은 패턴). 기존 {@code @SpringBootTest}
 * 컨텍스트 시그니처를 바꾸지 않도록 {@link IntegrationTestSupport} 를 그대로 상속만 한다.</p>
 */
@Tag("throughput")
class DiscogsResyncThroughputTest extends IntegrationTestSupport {

	private static final int CANDIDATE_COUNT = 10_000;
	private static final long RELEASE_ID_BASE = 900_000_000L;
	private static final BigDecimal PRICE = new BigDecimal("30000");
	// 리미터가 정의하는 앞항(30 req/min 배정 = 2초/건). 이 값 대비 뒷항(건별 로컬 오버헤드)이
	// 무시할 수 있는 수준인지가 이 테스트의 유일한 질문이다.
	private static final double RATE_LIMIT_MS_PER_CALL = 2_000.0;
	private static final double NEGLIGIBLE_RATIO = 0.01;
	private static final Path REPORT_PATH = Path.of("build", "reports", "discogs-resync-throughput.md");

	@Autowired
	private DiscogsResyncService discogsResyncService;

	@Autowired
	private PressingLookupClient pressingLookupClient;

	@Autowired
	private ProductRepository productRepository;

	@Autowired
	private AlbumRepository albumRepository;

	@Autowired
	private ArtistRepository artistRepository;

	@Test
	@DisplayName("apply() 를 1만 건 반복해도 중복 호출이 없고 건별 오버헤드가 레이트리밋 대비 무시할 수 있다")
	void staysWithinNegligibleOverheadForTenThousandCandidates() throws IOException {
		// given
		FakePressingLookupClient fake = (FakePressingLookupClient)pressingLookupClient;
		fake.reset();
		List<long[]> candidates = seedCandidates(fake);

		// when
		long startedAt = System.nanoTime();
		for (long[] candidate : candidates) {
			long productId = candidate[0];
			long releaseId = candidate[1];
			DiscogsReleaseResponse release = pressingLookupClient.getRelease(releaseId);
			discogsResyncService.apply(productId, releaseId, release);
		}
		long elapsedNanos = System.nanoTime() - startedAt;

		// then: 중복 호출이 없다 - 건수만큼만 불렀다
		assertThat(fake.totalReleaseCalls()).isEqualTo(CANDIDATE_COUNT);

		double totalMs = elapsedNanos / 1_000_000.0;
		double avgMsPerCandidate = totalMs / CANDIDATE_COUNT;
		double ratioToRateLimit = avgMsPerCandidate / RATE_LIMIT_MS_PER_CALL;
		String report = toMarkdown(totalMs, avgMsPerCandidate, ratioToRateLimit);
		System.out.println(report);
		writeReport(report);

		// then: 뒷항이 앞항의 1% 미만이다
		assertThat(ratioToRateLimit).isLessThan(NEGLIGIBLE_RATIO);
	}

	private List<long[]> seedCandidates(FakePressingLookupClient fake) {
		Artist artist = artistRepository.save(ArtistFixture.create("Throughput Artist"));
		Album album = albumRepository.save(AlbumFixture.create(artist, "Throughput Album"));
		List<long[]> candidates = new ArrayList<>(CANDIDATE_COUNT);
		for (int i = 0; i < CANDIDATE_COUNT; i++) {
			long releaseId = RELEASE_ID_BASE + i;
			Product product = Product.createImported(album, "Throughput Pressing " + i, artist, null, "Germany",
					1959, "CS 8163", "5012394144777", EditionType.STANDARD, PRICE, releaseId, null);
			product.resume();
			product = productRepository.save(product);
			candidates.add(new long[] {product.getId(), releaseId});
			fake.addRelease(DiscogsFixture.releaseResponse(releaseId, null, "Miles Davis", "Columbia", "CS 8163",
					List.of(), "5012394144777", List.of("Jazz"), List.of(), 1959));
		}
		return candidates;
	}

	private String toMarkdown(double totalMs, double avgMsPerCandidate, double ratioToRateLimit) {
		return """
				# Discogs 재검증 건별 오버헤드 측정

				| 항목 | 값 |
				|---|---|
				| 후보 건수 | %d건 |
				| 실제 호출 횟수 | %d회 (중복 없음) |
				| 총 소요 시간 | %.0fms |
				| 건별 평균 오버헤드(DB 읽기+쓰기) | %.3fms |
				| 리미터 앞항(30 req/min = 2000ms/건) 대비 | %.3f%% |
				""".formatted(CANDIDATE_COUNT, CANDIDATE_COUNT, totalMs, avgMsPerCandidate, ratioToRateLimit * 100);
	}

	private void writeReport(String report) throws IOException {
		Files.createDirectories(REPORT_PATH.getParent());
		Files.writeString(REPORT_PATH, report);
	}
}
