package com.groove.recommend;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Clock;
import java.time.LocalDateTime;
import java.time.temporal.ChronoUnit;
import java.util.UUID;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

import com.groove.fixture.ArtistFixture;
import com.groove.fixture.MemberFixture;
import com.groove.fixture.ProductFixture;
import com.groove.fixture.ProductViewLogFixture;
import com.groove.member.entity.Member;
import com.groove.member.repository.MemberRepository;
import com.groove.product.entity.Artist;
import com.groove.product.entity.Product;
import com.groove.product.repository.ArtistRepository;
import com.groove.product.repository.ProductRepository;
import com.groove.recommend.entity.ProductViewLog;
import com.groove.recommend.repository.ProductViewLogRepository;
import com.groove.recommend.scheduler.ProductViewLogCleanupScheduler;
import com.groove.recommend.service.ProductViewLogCleanupService;
import com.groove.support.IntegrationTestSupport;

class ProductViewLogCleanupIntegrationTest extends IntegrationTestSupport {

	@Autowired
	private ArtistRepository artistRepository;

	@Autowired
	private ProductRepository productRepository;

	@Autowired
	private MemberRepository memberRepository;

	@Autowired
	private ProductViewLogRepository productViewLogRepository;

	@Autowired
	private ProductViewLogCleanupScheduler productViewLogCleanupScheduler;

	@Autowired
	private ProductViewLogCleanupService productViewLogCleanupService;

	@Autowired
	private Clock clock;

	private Product createProduct() {
		Artist artist = artistRepository.save(ArtistFixture.create());
		return productRepository.save(ProductFixture.create(artist));
	}

	private Member createMember() {
		return memberRepository.save(MemberFixture.create("viewer-" + UUID.randomUUID() + "@groove.com"));
	}

	@Nested
	@DisplayName("cleanUp()")
	class CleanUp {

		@Test
		@DisplayName("90일이 지난 조회 로그를 삭제한다")
		void deletesLogsOlderThanRetentionPeriod() {
			// given
			Product product = createProduct();
			Member member = createMember();
			LocalDateTime now = LocalDateTime.now(clock).truncatedTo(ChronoUnit.SECONDS);
			ProductViewLog expired = productViewLogRepository.save(
					ProductViewLogFixture.create(member, product, now.minusDays(91)));
			ProductViewLog recent = productViewLogRepository.save(
					ProductViewLogFixture.create(member, product, now.minusDays(89)));

			// when
			productViewLogCleanupScheduler.cleanUp();

			// then
			assertThat(productViewLogRepository.existsById(expired.getId())).isFalse();
			assertThat(productViewLogRepository.existsById(recent.getId())).isTrue();
		}

		@Test
		@DisplayName("삭제 대상이 없으면 아무 로그도 지우지 않는다")
		void deletesNothingWhenNoLogsExpired() {
			// given
			Product product = createProduct();
			Member member = createMember();
			LocalDateTime now = LocalDateTime.now(clock).truncatedTo(ChronoUnit.SECONDS);
			ProductViewLog recent = productViewLogRepository.save(
					ProductViewLogFixture.create(member, product, now.minusDays(1)));

			// when
			productViewLogCleanupScheduler.cleanUp();

			// then
			assertThat(productViewLogRepository.existsById(recent.getId())).isTrue();
		}
	}

	@Nested
	@DisplayName("deleteBatch()")
	class DeleteBatch {

		@Test
		@DisplayName("size 만큼만 삭제하고 삭제된 행 수를 반환한다")
		void deletesUpToGivenSizeAndReturnsDeletedCount() {
			// given
			Product product = createProduct();
			Member member = createMember();
			LocalDateTime now = LocalDateTime.now(clock).truncatedTo(ChronoUnit.SECONDS);
			ProductViewLog first = productViewLogRepository.save(
					ProductViewLogFixture.create(member, product, now.minusDays(91)));
			ProductViewLog second = productViewLogRepository.save(
					ProductViewLogFixture.create(member, product, now.minusDays(92)));

			// when
			int deleted = productViewLogCleanupService.deleteBatch(now.minusDays(90), 1);

			// then
			assertThat(deleted).isEqualTo(1);
			boolean firstExists = productViewLogRepository.existsById(first.getId());
			boolean secondExists = productViewLogRepository.existsById(second.getId());
			assertThat(firstExists ^ secondExists).isTrue();
		}
	}
}
