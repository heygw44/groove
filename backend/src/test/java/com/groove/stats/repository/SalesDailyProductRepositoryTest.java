package com.groove.stats.repository;

import static org.assertj.core.api.Assertions.assertThat;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.temporal.ChronoUnit;
import java.util.List;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

import com.groove.fixture.ArtistFixture;
import com.groove.fixture.ProductFixture;
import com.groove.product.entity.Artist;
import com.groove.product.entity.Product;
import com.groove.stats.entity.SalesDailyProduct;
import com.groove.stats.entity.SalesDailyProductId;
import com.groove.support.DataJpaTestSupport;

import jakarta.persistence.EntityManager;

/** 공유 테스트 DB 오염을 피하기 위해 이 테스트만 쓰는 먼 날짜를 쓴다. */
class SalesDailyProductRepositoryTest extends DataJpaTestSupport {

	private static final LocalDate SAVE_DATE = LocalDate.of(2031, 8, 5);

	@Autowired
	private SalesDailyProductRepository salesDailyProductRepository;

	@Autowired
	private EntityManager em;

	private Product product;

	@BeforeEach
	void setUp() {
		Artist artist = ArtistFixture.create("SDP Artist");
		em.persist(artist);
		product = ProductFixture.create(artist, "SDP Product", new BigDecimal("30000"));
		em.persist(product.getAlbum());
		em.persist(product);
		em.flush();
	}

	@Nested
	@DisplayName("save()")
	class Save {

		@Test
		@DisplayName("같은 (sale_date, product_id) 로 두 번 저장하면 갱신되고 행이 하나만 남는다")
		void upsertsByCompositeKey() {
			// given
			LocalDateTime firstAggregatedAt = LocalDateTime.of(2031, 8, 5, 1, 0, 0);
			SalesDailyProduct first = SalesDailyProduct.of(SAVE_DATE, product, 1, new BigDecimal("30000"), 1,
					firstAggregatedAt);
			salesDailyProductRepository.save(first);
			salesDailyProductRepository.flush();
			em.clear();

			// when
			SalesDailyProduct reloaded = salesDailyProductRepository
					.findById(SalesDailyProductId.of(SAVE_DATE, product.getId()))
					.orElseThrow();
			LocalDateTime secondAggregatedAt = LocalDateTime.of(2031, 8, 5, 2, 0, 0);
			SalesDailyProduct second = SalesDailyProduct.of(SAVE_DATE, product, 3, new BigDecimal("90000"), 2,
					secondAggregatedAt);
			salesDailyProductRepository.save(second);
			salesDailyProductRepository.flush();

			// then
			assertThat(reloaded).isNotNull();
			List<SalesDailyProduct> found = salesDailyProductRepository.findAllByIdSaleDate(SAVE_DATE);
			assertThat(found).hasSize(1);
			SalesDailyProduct saved = found.get(0);
			assertThat(saved.getSoldQuantity()).isEqualTo(3);
			assertThat(saved.getSalesAmount()).isEqualByComparingTo(new BigDecimal("90000"));
			assertThat(saved.getOrderCount()).isEqualTo(2);
			assertThat(saved.getAggregatedAt().truncatedTo(ChronoUnit.SECONDS))
					.isEqualTo(secondAggregatedAt.truncatedTo(ChronoUnit.SECONDS));
		}
	}

	@Nested
	@DisplayName("findAllByIdSaleDate()")
	class FindAllByIdSaleDate {

		@Test
		@DisplayName("다른 날짜의 행은 섞이지 않는다")
		void doesNotMixOtherDates() {
			// given
			LocalDate otherDate = LocalDate.of(2031, 8, 6);
			LocalDateTime aggregatedAt = LocalDateTime.of(2031, 8, 5, 0, 0);
			salesDailyProductRepository.save(SalesDailyProduct.of(SAVE_DATE, product, 1, BigDecimal.TEN, 1,
					aggregatedAt));
			salesDailyProductRepository.save(SalesDailyProduct.of(otherDate, product, 5, BigDecimal.TEN, 1,
					aggregatedAt));
			salesDailyProductRepository.flush();

			// when
			List<SalesDailyProduct> result = salesDailyProductRepository.findAllByIdSaleDate(SAVE_DATE);

			// then
			assertThat(result).hasSize(1);
			assertThat(result.get(0).getSoldQuantity()).isEqualTo(1);
		}
	}
}
