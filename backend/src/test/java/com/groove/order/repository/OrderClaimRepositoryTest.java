package com.groove.order.repository;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

import com.groove.fixture.ArtistFixture;
import com.groove.fixture.MemberFixture;
import com.groove.fixture.OrderFixture;
import com.groove.fixture.ProductFixture;
import com.groove.member.entity.Member;
import com.groove.member.repository.MemberRepository;
import com.groove.order.entity.Order;
import com.groove.order.entity.OrderClaim;
import com.groove.order.entity.OrderClaimStatus;
import com.groove.order.entity.OrderClaimType;
import com.groove.order.entity.OrderItem;
import com.groove.product.entity.Artist;
import com.groove.product.entity.Product;
import com.groove.product.repository.AlbumRepository;
import com.groove.product.repository.ArtistRepository;
import com.groove.product.repository.ProductRepository;
import com.groove.support.DataJpaTestSupport;

class OrderClaimRepositoryTest extends DataJpaTestSupport {

	private static final LocalDateTime NOW = LocalDateTime.of(2026, 9, 29, 12, 0);

	@Autowired
	private OrderClaimRepository orderClaimRepository;

	@Autowired
	private OrderRepository orderRepository;

	@Autowired
	private MemberRepository memberRepository;

	@Autowired
	private ArtistRepository artistRepository;

	@Autowired
	private ProductRepository productRepository;

	@Autowired
	private AlbumRepository albumRepository;

	private long count(List<OrderClaimCountRow> rows, OrderClaimType type, OrderClaimStatus status) {
		return rows.stream()
				.filter(row -> row.getType() == type && row.getStatus() == status)
				.mapToLong(OrderClaimCountRow::getCount)
				.sum();
	}

	/** 주문번호가 픽스처에서 고정이라 한 주문에 상품주문 여러 개를 담아 만든다. */
	private List<OrderItem> saveOrderItems(String key, int count) {
		Member member = memberRepository.save(MemberFixture.create(key + "@groove.com"));
		Artist artist = artistRepository.save(ArtistFixture.create(key));
		List<Product> products = new ArrayList<>();
		for (int i = 0; i < count; i++) {
			Product created = ProductFixture.create(artist, key + "-" + i);
			albumRepository.save(created.getAlbum());
			products.add(productRepository.save(created));
		}
		Order order = orderRepository.saveAndFlush(OrderFixture.createWithItems(member, products));
		return order.getItems();
	}

	@Nested
	@DisplayName("countByTypeAndStatus()")
	class CountByTypeAndStatus {

		@Test
		@DisplayName("클레임을 저장하면 해당 유형·상태의 건수만 늘어난다")
		void increasesOnlyMatchingCounts() {
			// given
			List<OrderClaimCountRow> before = orderClaimRepository.countByTypeAndStatus();
			List<OrderItem> items = saveOrderItems("claim-count", 2);
			orderClaimRepository.save(OrderClaim.requestCancel(items.get(0), "사유", null, NOW));
			OrderClaim collecting = OrderClaim.requestReturn(items.get(1), "사유", null, NOW);
			collecting.startCollecting();
			orderClaimRepository.saveAndFlush(collecting);

			// when
			List<OrderClaimCountRow> after = orderClaimRepository.countByTypeAndStatus();

			// then
			assertThat(count(after, OrderClaimType.CANCEL, OrderClaimStatus.REQUESTED))
					.isEqualTo(count(before, OrderClaimType.CANCEL, OrderClaimStatus.REQUESTED) + 1);
			assertThat(count(after, OrderClaimType.RETURN, OrderClaimStatus.COLLECTING))
					.isEqualTo(count(before, OrderClaimType.RETURN, OrderClaimStatus.COLLECTING) + 1);
			assertThat(count(after, OrderClaimType.RETURN, OrderClaimStatus.REQUESTED))
					.isEqualTo(count(before, OrderClaimType.RETURN, OrderClaimStatus.REQUESTED));
			assertThat(count(after, OrderClaimType.CANCEL, OrderClaimStatus.DONE))
					.isEqualTo(count(before, OrderClaimType.CANCEL, OrderClaimStatus.DONE));
		}
	}
}
