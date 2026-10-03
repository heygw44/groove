package com.groove.product;

import static org.assertj.core.api.Assertions.assertThat;

import java.math.BigDecimal;
import java.util.Map;
import java.util.UUID;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.support.TransactionTemplate;

import com.groove.fixture.ArtistFixture;
import com.groove.fixture.MemberFixture;
import com.groove.fixture.OrderFixture;
import com.groove.fixture.ProductFixture;
import com.groove.fixture.ReviewFixture;
import com.groove.member.entity.Member;
import com.groove.member.repository.MemberRepository;
import com.groove.order.entity.Order;
import com.groove.order.entity.OrderItemStatus;
import com.groove.order.repository.OrderRepository;
import com.groove.product.entity.Artist;
import com.groove.product.entity.Product;
import com.groove.product.entity.ProductStatus;
import com.groove.product.repository.AlbumRepository;
import com.groove.product.repository.ArtistRepository;
import com.groove.product.repository.ProductRepository;
import com.groove.review.service.ReviewService;
import com.groove.support.IntegrationTestSupport;

/**
 * 리뷰 집계 컬럼은 refreshReviewStats 네이티브 UPDATE 로만 갱신된다. 상품을 먼저 읽어 둔 트랜잭션이
 * 그 사이 커밋된 리뷰 집계를 전 컬럼 UPDATE 로 옛 값으로 되쓰지 않는지 실제 커밋 순서로 확인한다.
 */
class ProductReviewStatsOverwriteIntegrationTest extends IntegrationTestSupport {

	@Autowired
	private ArtistRepository artistRepository;

	@Autowired
	private AlbumRepository albumRepository;

	@Autowired
	private ProductRepository productRepository;

	@Autowired
	private MemberRepository memberRepository;

	@Autowired
	private OrderRepository orderRepository;

	@Autowired
	private ReviewService reviewService;

	@Autowired
	private PlatformTransactionManager transactionManager;

	@Autowired
	private JdbcTemplate jdbcTemplate;

	@Nested
	@DisplayName("Product 저장")
	class SaveProduct {

		@Test
		@DisplayName("상품 수정 트랜잭션과 리뷰 작성이 겹쳐도 review_count 가 실제 리뷰 수와 같다")
		void keepsReviewCountWhenProductUpdatedConcurrentlyWithReview() {
			// given: 리뷰 1건(평점 5)이 커밋된 상품과, 두 번째 리뷰를 쓸 구매확정 회원
			Artist artist = artistRepository.save(ArtistFixture.create());
			Product createdProduct = ProductFixture.create(artist);
			albumRepository.save(createdProduct.getAlbum());
			Product product = productRepository.save(createdProduct);
			Long productId = product.getId();
			Long firstReviewerId = createPurchaseConfirmedMember(product);
			Long secondReviewerId = createPurchaseConfirmedMember(product);
			reviewService.create(productId, firstReviewerId, ReviewFixture.createRequest(5));

			TransactionTemplate outer = new TransactionTemplate(transactionManager);
			TransactionTemplate inner = new TransactionTemplate(transactionManager);
			inner.setPropagationBehavior(TransactionDefinition.PROPAGATION_REQUIRES_NEW);

			// when: A 가 잠금 없는 스냅샷 읽기로 review_count=1 을 들고 있는 동안 B 가 리뷰를 커밋하고, 그 뒤 A 가 상품을 숨긴다.
			outer.executeWithoutResult(status -> {
				Product loaded = productRepository.findById(productId).orElseThrow();
				inner.executeWithoutResult(innerStatus ->
						reviewService.create(productId, secondReviewerId, ReviewFixture.createRequest(2)));
				loaded.hide();
			});

			// then: A 의 UPDATE 는 반영되고, 리뷰 집계는 B 가 커밋한 실제 값으로 남는다.
			Map<String, Object> row = jdbcTemplate.queryForMap(
					"SELECT status, review_count, avg_rating FROM product WHERE id = ?", productId);
			Integer actualCount = jdbcTemplate.queryForObject(
					"SELECT COUNT(*) FROM review WHERE product_id = ?", Integer.class, productId);
			BigDecimal actualAverage = jdbcTemplate.queryForObject(
					"SELECT ROUND(AVG(rating), 1) FROM review WHERE product_id = ?", BigDecimal.class, productId);

			assertThat(row.get("status")).isEqualTo(ProductStatus.HIDDEN.name());
			assertThat(actualCount).isEqualTo(2);
			assertThat(((Number) row.get("review_count")).intValue()).isEqualTo(actualCount);
			assertThat(actualAverage).isEqualByComparingTo("3.5");
			assertThat((BigDecimal) row.get("avg_rating")).isEqualByComparingTo(actualAverage);
		}

		private Long createPurchaseConfirmedMember(Product product) {
			Member member = memberRepository.save(
					MemberFixture.create("reviewer-" + UUID.randomUUID() + "@groove.com"));
			// createWithItem 은 주문번호가 고정이라 회원 둘을 심으면 유니크 키가 깨진다.
			Order order = OrderFixture.create(member, "20260903-RS" + UUID.randomUUID().toString().substring(0, 8));
			order.addItem(product, 1);
			OrderFixture.markDelivered(order);
			OrderFixture.markItemsStatus(order, OrderItemStatus.PURCHASE_CONFIRMED);
			orderRepository.save(order);
			return member.getId();
		}
	}
}
