package com.groove.limited;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Clock;
import java.time.LocalDateTime;
import java.util.Optional;
import java.util.UUID;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.redis.core.StringRedisTemplate;

import com.groove.fixture.AddressFixture;
import com.groove.fixture.ArtistFixture;
import com.groove.fixture.LimitedDropFixture;
import com.groove.fixture.MemberFixture;
import com.groove.fixture.OrderFixture;
import com.groove.fixture.ProductFixture;
import com.groove.fixture.StockFixture;
import com.groove.inventory.entity.Stock;
import com.groove.inventory.entity.StockChangeType;
import com.groove.inventory.repository.StockHistoryRepository;
import com.groove.inventory.repository.StockRepository;
import com.groove.limited.dto.LimitedPurchaseResponse;
import com.groove.limited.entity.LimitedDrop;
import com.groove.limited.entity.LimitedDropStatus;
import com.groove.limited.entity.LimitedPurchase;
import com.groove.limited.repository.LimitedDropRepository;
import com.groove.limited.repository.LimitedPurchaseRepository;
import com.groove.limited.scheduler.LimitedDropScheduler;
import com.groove.limited.service.LimitedDropRedisService;
import com.groove.limited.service.LimitedPurchaseService;
import com.groove.member.entity.Address;
import com.groove.member.entity.Member;
import com.groove.member.repository.AddressRepository;
import com.groove.member.repository.MemberRepository;
import com.groove.order.entity.Order;
import com.groove.order.entity.OrderStatus;
import com.groove.order.repository.OrderRepository;
import com.groove.order.scheduler.OrderExpirationScheduler;
import com.groove.product.entity.Artist;
import com.groove.product.entity.Product;
import com.groove.product.repository.ArtistRepository;
import com.groove.product.repository.ProductRepository;
import com.groove.support.IntegrationTestSupport;

class LimitedDropSchedulerIntegrationTest extends IntegrationTestSupport {

	@Autowired
	private ArtistRepository artistRepository;

	@Autowired
	private ProductRepository productRepository;

	@Autowired
	private StockRepository stockRepository;

	@Autowired
	private StockHistoryRepository stockHistoryRepository;

	@Autowired
	private LimitedDropRepository limitedDropRepository;

	@Autowired
	private LimitedPurchaseRepository limitedPurchaseRepository;

	@Autowired
	private LimitedDropRedisService limitedDropRedisService;

	@Autowired
	private StringRedisTemplate redisTemplate;

	@Autowired
	private MemberRepository memberRepository;

	@Autowired
	private AddressRepository addressRepository;

	@Autowired
	private OrderRepository orderRepository;

	@Autowired
	private LimitedPurchaseService limitedPurchaseService;

	@Autowired
	private LimitedDropScheduler limitedDropScheduler;

	@Autowired
	private OrderExpirationScheduler orderExpirationScheduler;

	@Autowired
	private Clock clock;

	private Product createProduct() {
		Artist artist = artistRepository.save(ArtistFixture.create());
		return productRepository.save(ProductFixture.create(artist));
	}

	private Member createMember() {
		return memberRepository.save(MemberFixture.create("buyer-" + UUID.randomUUID() + "@groove.com"));
	}

	@Nested
	@DisplayName("run()")
	class Run {

		@Test
		@DisplayName("오픈 시각이 지난 SCHEDULED 드롭을 OPEN 으로 바꾸고 Redis 재고를 세팅한다")
		void opensScheduledDropPastOpenAt() {
			// given
			Product product = createProduct();
			stockRepository.saveAndFlush(StockFixture.create(product, 10));
			LocalDateTime now = LocalDateTime.now(clock);
			LimitedDrop drop = LimitedDropFixture.scheduled(product, 10, 2);
			LimitedDropFixture.withOpenAt(drop, now.minusMinutes(1));
			LimitedDropFixture.withCloseAt(drop, now.plusHours(1));
			LimitedDrop saved = limitedDropRepository.saveAndFlush(drop);
			limitedDropRedisService.clear(saved.getId());

			// when
			limitedDropScheduler.run();

			// then
			LimitedDrop reloaded = limitedDropRepository.findById(saved.getId()).orElseThrow();
			assertThat(reloaded.getStatus()).isEqualTo(LimitedDropStatus.OPEN);
			assertThat(redisTemplate.opsForValue().get(LimitedDropRedisService.stockKey(saved.getId())))
					.isEqualTo("10");

			limitedDropRedisService.clear(saved.getId());
		}

		@Test
		@DisplayName("마감 시각이 지난 OPEN 드롭을 CLOSED 로 바꾸고 Redis 키를 지운다")
		void closesOpenDropPastCloseAt() {
			// given
			Product product = createProduct();
			stockRepository.saveAndFlush(StockFixture.create(product, 10));
			LocalDateTime now = LocalDateTime.now(clock);
			LimitedDrop drop = LimitedDropFixture.open(product, 10);
			LimitedDropFixture.withOpenAt(drop, now.minusHours(1));
			LimitedDropFixture.withCloseAt(drop, now.minusMinutes(1));
			LimitedDrop saved = limitedDropRepository.saveAndFlush(drop);
			limitedDropRedisService.clear(saved.getId());
			limitedDropRedisService.initStock(saved.getId(), 10);

			// when
			limitedDropScheduler.run();

			// then
			LimitedDrop reloaded = limitedDropRepository.findById(saved.getId()).orElseThrow();
			assertThat(reloaded.getStatus()).isEqualTo(LimitedDropStatus.CLOSED);
			assertThat(redisTemplate.hasKey(LimitedDropRedisService.stockKey(saved.getId()))).isFalse();
			assertThat(redisTemplate.hasKey(LimitedDropRedisService.buyersKey(saved.getId()))).isFalse();
		}
	}

	@Nested
	@DisplayName("한정반 구매 후 주문 만료 흐름")
	class PurchaseThenExpire {

		@Test
		@DisplayName("주문이 만료되면 판매량·재고·Redis 선점이 모두 되돌아가고 드롭은 다시 OPEN 이다")
		void revertsSaleStockAndRedisWhenOrderExpires() {
			// given
			Product product = createProduct();
			stockRepository.saveAndFlush(StockFixture.create(product, 1));
			LocalDateTime now = LocalDateTime.now(clock);
			LimitedDrop drop = LimitedDropFixture.scheduled(product, 1, 1);
			drop.open();
			LimitedDropFixture.withOpenAt(drop, now.minusHours(1));
			LimitedDropFixture.withCloseAt(drop, now.plusHours(1));
			LimitedDrop saved = limitedDropRepository.saveAndFlush(drop);
			Long dropId = saved.getId();
			limitedDropRedisService.clear(dropId);
			limitedDropRedisService.initStock(dropId, 1);

			Member member = createMember();
			Address address = addressRepository.save(AddressFixture.create(member));

			// when: 구매로 선점
			LimitedPurchaseResponse purchaseResponse = limitedPurchaseService.purchase(dropId, member.getId(),
					address.getId());

			LimitedDrop soldOut = limitedDropRepository.findById(dropId).orElseThrow();
			assertThat(soldOut.getStatus()).isEqualTo(LimitedDropStatus.SOLD_OUT);
			assertThat(redisTemplate.opsForValue().get(LimitedDropRedisService.stockKey(dropId))).isEqualTo("0");
			assertThat(redisTemplate.opsForSet().isMember(LimitedDropRedisService.buyersKey(dropId),
					member.getId().toString())).isTrue();

			// and: 주문 만료 시각을 과거로 되돌린다
			Order order = orderRepository.findById(purchaseResponse.orderId()).orElseThrow();
			OrderFixture.withExpiresAt(order, now.minusMinutes(1));
			orderRepository.saveAndFlush(order);

			// when: 주문 만료 스케줄러 실행
			orderExpirationScheduler.expireOrders();

			// then
			Order canceledOrder = orderRepository.findById(order.getId()).orElseThrow();
			assertThat(canceledOrder.getStatus()).isEqualTo(OrderStatus.CANCELED);
			assertThat(canceledOrder.getCancelReason()).isEqualTo(Order.EXPIRED_CANCEL_REASON);

			Optional<LimitedPurchase> purchase = limitedPurchaseRepository.findByOrderId(order.getId());
			assertThat(purchase).isEmpty();

			LimitedDrop reopened = limitedDropRepository.findById(dropId).orElseThrow();
			assertThat(reopened.getSoldCount()).isZero();
			assertThat(reopened.getStatus()).isEqualTo(LimitedDropStatus.OPEN);

			Stock reloadedStock = stockRepository.findByProductId(product.getId()).orElseThrow();
			assertThat(reloadedStock.getQuantity()).isEqualTo(1);
			assertThat(stockHistoryRepository.findAllByStockIdOrderByCreatedAtAsc(reloadedStock.getId()).stream()
					.anyMatch(history -> history.getChangeType() == StockChangeType.CANCEL)).isTrue();

			assertThat(redisTemplate.opsForValue().get(LimitedDropRedisService.stockKey(dropId))).isEqualTo("1");
			assertThat(redisTemplate.opsForSet().isMember(LimitedDropRedisService.buyersKey(dropId),
					member.getId().toString())).isFalse();

			limitedDropRedisService.clear(dropId);
		}

		@Test
		@DisplayName("마감 시각이 이미 지났으면 만료돼도 SOLD_OUT 을 유지한다")
		void staysSoldOutWhenCloseAtAlreadyPassedBeforeExpiration() {
			// given
			Product product = createProduct();
			stockRepository.saveAndFlush(StockFixture.create(product, 1));
			LocalDateTime now = LocalDateTime.now(clock);
			LimitedDrop drop = LimitedDropFixture.scheduled(product, 1, 1);
			drop.open();
			LimitedDropFixture.withOpenAt(drop, now.minusHours(1));
			LimitedDropFixture.withCloseAt(drop, now.plusHours(1));
			LimitedDrop saved = limitedDropRepository.saveAndFlush(drop);
			Long dropId = saved.getId();
			limitedDropRedisService.clear(dropId);
			limitedDropRedisService.initStock(dropId, 1);

			Member member = createMember();
			Address address = addressRepository.save(AddressFixture.create(member));

			LimitedPurchaseResponse purchaseResponse = limitedPurchaseService.purchase(dropId, member.getId(),
					address.getId());

			// and: 마감 시각을 과거로 옮긴다 (드롭 스케줄러는 돌리지 않는다)
			LimitedDrop soldOut = limitedDropRepository.findById(dropId).orElseThrow();
			LimitedDropFixture.withCloseAt(soldOut, now.minusMinutes(1));
			limitedDropRepository.saveAndFlush(soldOut);

			Order order = orderRepository.findById(purchaseResponse.orderId()).orElseThrow();
			OrderFixture.withExpiresAt(order, now.minusMinutes(1));
			orderRepository.saveAndFlush(order);

			// when
			orderExpirationScheduler.expireOrders();

			// then
			LimitedDrop reloaded = limitedDropRepository.findById(dropId).orElseThrow();
			assertThat(reloaded.getStatus()).isEqualTo(LimitedDropStatus.SOLD_OUT);
			assertThat(reloaded.getSoldCount()).isZero();

			limitedDropRedisService.clear(dropId);
		}
	}
}
