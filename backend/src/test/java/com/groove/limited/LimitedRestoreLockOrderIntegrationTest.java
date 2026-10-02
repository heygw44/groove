package com.groove.limited;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Clock;
import java.time.LocalDateTime;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.function.Supplier;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

import com.groove.fixture.AddressFixture;
import com.groove.fixture.ArtistFixture;
import com.groove.fixture.LimitedDropFixture;
import com.groove.fixture.MemberFixture;
import com.groove.fixture.OrderFixture;
import com.groove.fixture.ProductFixture;
import com.groove.fixture.StockFixture;
import com.groove.inventory.repository.StockRepository;
import com.groove.limited.dto.LimitedPurchaseResponse;
import com.groove.limited.entity.LimitedDrop;
import com.groove.limited.repository.LimitedDropRepository;
import com.groove.limited.repository.LimitedPurchaseRepository;
import com.groove.limited.service.LimitedDropRedisService;
import com.groove.limited.service.LimitedPurchaseWriter;
import com.groove.member.entity.Address;
import com.groove.member.entity.Member;
import com.groove.member.repository.AddressRepository;
import com.groove.member.repository.MemberRepository;
import com.groove.order.entity.Order;
import com.groove.order.entity.OrderClaim;
import com.groove.order.entity.OrderItem;
import com.groove.order.entity.OrderItemClaimStatus;
import com.groove.order.entity.OrderItemStatus;
import com.groove.order.entity.OrderStatus;
import com.groove.order.repository.OrderClaimRepository;
import com.groove.order.repository.OrderRepository;
import com.groove.order.service.OrderClaimFinalizeService;
import com.groove.order.service.OrderExpirationWriter;
import com.groove.payment.client.PaymentClient;
import com.groove.product.entity.Artist;
import com.groove.product.entity.Product;
import com.groove.product.repository.AlbumRepository;
import com.groove.product.repository.ArtistRepository;
import com.groove.product.repository.ProductRepository;
import com.groove.support.IntegrationTestSupport;

/**
 * 한정반 주문의 취소·만료 복원이 구매와 같은 락 순서(드롭 → 재고)를 지키는지 검증한다. 구매 트랜잭션을 흉내 낸
 * 스레드가 드롭 행을 먼저 잠근 채 버티는 동안 복원을 시작하고, 그 뒤 같은 트랜잭션에서 재고를 차감한다. 복원이
 * 재고를 먼저 잠그면 두 트랜잭션이 서로의 락을 기다려 InnoDB 가 한쪽을 데드락으로 끊고(또는 락 대기 타임아웃),
 * 드롭부터 잠그면 복원은 재고에 손대기 전에 드롭에서 기다리므로 구매가 먼저 끝나고 복원이 이어진다.
 */
class LimitedRestoreLockOrderIntegrationTest extends IntegrationTestSupport {

	private static final int TOTAL_QUANTITY = 10;
	private static final long HOLD_MILLIS = 700L;
	private static final long TIMEOUT_SECONDS = 20L;

	@Autowired
	private ArtistRepository artistRepository;

	@Autowired
	private AlbumRepository albumRepository;

	@Autowired
	private ProductRepository productRepository;

	@Autowired
	private StockRepository stockRepository;

	@Autowired
	private LimitedDropRepository limitedDropRepository;

	@Autowired
	private LimitedPurchaseRepository limitedPurchaseRepository;

	@Autowired
	private LimitedDropRedisService limitedDropRedisService;

	@Autowired
	private LimitedPurchaseWriter limitedPurchaseWriter;

	@Autowired
	private MemberRepository memberRepository;

	@Autowired
	private AddressRepository addressRepository;

	@Autowired
	private OrderRepository orderRepository;

	@Autowired
	private OrderClaimRepository orderClaimRepository;

	@Autowired
	private OrderExpirationWriter orderExpirationWriter;

	@Autowired
	private OrderClaimFinalizeService orderClaimFinalizeService;

	@Autowired
	private PlatformTransactionManager transactionManager;

	@Autowired
	private Clock clock;

	@MockitoBean
	private PaymentClient paymentClient;

	private Long dropId;
	private Long productId;

	@AfterEach
	void tearDown() {
		if (dropId != null) {
			limitedDropRedisService.clear(dropId);
		}
	}

	private void prepareOpenDrop() {
		Artist artist = artistRepository.save(ArtistFixture.create());
		Product created = ProductFixture.create(artist);
		albumRepository.save(created.getAlbum());
		Product product = productRepository.save(created);
		stockRepository.saveAndFlush(StockFixture.create(product, TOTAL_QUANTITY));

		LimitedDrop drop = LimitedDropFixture.open(product, TOTAL_QUANTITY);
		LocalDateTime now = LocalDateTime.now(clock);
		LimitedDropFixture.withOpenAt(drop, now.minusHours(1));
		LimitedDropFixture.withCloseAt(drop, now.plusHours(1));
		limitedDropRepository.saveAndFlush(drop);

		dropId = drop.getId();
		productId = product.getId();
		// create-drop 로 PK 가 재사용될 수 있어 다른 테스트가 남긴 낡은 키를 먼저 지운다.
		limitedDropRedisService.clear(dropId);
	}

	private Purchased purchase() {
		Member member = memberRepository.save(MemberFixture.create("buyer-" + UUID.randomUUID() + "@groove.com"));
		Address address = addressRepository.save(AddressFixture.create(member));
		LimitedPurchaseResponse response = limitedPurchaseWriter.write(dropId, member.getId(), address.getId(),
				productId);
		return new Purchased(member.getId(), response.orderId());
	}

	/**
	 * 구매({@code LimitedPurchaseWriter#write})의 락 순서를 그대로 밟는다: 드롭 행을 잠그고, 복원 트랜잭션이 락 대기에
	 * 들어갈 시간을 준 뒤 재고를 차감한다. 결과는 롤백해 재고·판매 수 검증을 복원 결과만으로 할 수 있게 한다.
	 */
	private Future<Integer> holdDropThenDecreaseStock(ExecutorService executor, CountDownLatch dropLocked) {
		TransactionTemplate template = new TransactionTemplate(transactionManager);
		return executor.submit(() -> template.execute(status -> {
			limitedDropRepository.findByIdForUpdate(dropId).orElseThrow();
			dropLocked.countDown();
			sleepQuietly(HOLD_MILLIS);
			int updatedRows = stockRepository.decreaseIfAvailable(productId, 1);
			status.setRollbackOnly();
			return updatedRows;
		}));
	}

	private <T> RaceResult<T> raceWithPurchase(Supplier<T> restoreAction) throws Exception {
		CountDownLatch dropLocked = new CountDownLatch(1);
		ExecutorService executor = Executors.newFixedThreadPool(2);
		try {
			Future<Integer> purchaser = holdDropThenDecreaseStock(executor, dropLocked);
			Future<T> restorer = executor.submit(() -> {
				assertThat(dropLocked.await(TIMEOUT_SECONDS, TimeUnit.SECONDS)).isTrue();
				return restoreAction.get();
			});
			int decreasedRows = purchaser.get(TIMEOUT_SECONDS, TimeUnit.SECONDS);
			T restored = restorer.get(TIMEOUT_SECONDS, TimeUnit.SECONDS);
			return new RaceResult<>(decreasedRows, restored);
		} finally {
			executor.shutdownNow();
		}
	}

	private void assertRestored(Purchased purchased) {
		assertThat(limitedPurchaseRepository.existsByDropIdAndMemberId(dropId, purchased.memberId())).isFalse();
		assertThat(limitedDropRepository.findById(dropId).orElseThrow().getSoldCount()).isZero();
		assertThat(stockRepository.findByProductId(productId).orElseThrow().getQuantity())
				.isEqualTo(TOTAL_QUANTITY);
	}

	@Nested
	@DisplayName("checkExpirable()")
	class CheckExpirable {

		@Test
		@DisplayName("구매가 드롭 락을 쥔 채 재고를 차감해도 만료 복원은 드롭에서 기다려 데드락 없이 둘 다 끝난다")
		void expiresWithoutDeadlockWhilePurchaseHoldsDrop() throws Exception {
			// given
			prepareOpenDrop();
			Purchased purchased = purchase();
			Order order = orderRepository.findById(purchased.orderId()).orElseThrow();
			OrderFixture.withExpiresAt(order, LocalDateTime.now(clock).minusMinutes(1));
			orderRepository.saveAndFlush(order);

			// when
			RaceResult<Boolean> result = raceWithPurchase(() -> orderExpirationWriter
					.checkExpirable(purchased.orderId(), LocalDateTime.now(clock)).isPresent());

			// then
			assertThat(result.decreasedRows()).isEqualTo(1);
			assertThat(result.restored()).isTrue();
			assertThat(orderRepository.findById(purchased.orderId()).orElseThrow().getStatus())
					.isEqualTo(OrderStatus.CANCELED);
			assertRestored(purchased);
		}
	}

	@Nested
	@DisplayName("applyRefundDone()")
	class ApplyRefundDone {

		@Test
		@DisplayName("구매가 드롭 락을 쥔 채 재고를 차감해도 취소 클레임 복원은 드롭에서 기다려 데드락 없이 둘 다 끝난다")
		void restoresItemWithoutDeadlockWhilePurchaseHoldsDrop() throws Exception {
			// given
			prepareOpenDrop();
			Purchased purchased = purchase();
			TransactionTemplate template = new TransactionTemplate(transactionManager);
			Long claimId = template.execute(status -> {
				Order order = orderRepository.findWithItemsById(purchased.orderId()).orElseThrow();
				LocalDateTime now = LocalDateTime.now(clock);
				order.markPaid();
				order.place(now);
				OrderItem item = order.getItems().get(0);
				item.markClaimRequested(OrderItemClaimStatus.CANCEL_REQUEST);
				return orderClaimRepository.save(OrderClaim.requestCancel(item, "고객 변심", null, now)).getId();
			});

			// when
			RaceResult<Boolean> result = raceWithPurchase(() -> {
				orderClaimFinalizeService.applyRefundDone(claimId, null);
				return true;
			});

			// then
			assertThat(result.decreasedRows()).isEqualTo(1);
			Order reloaded = orderRepository.findWithItemsById(purchased.orderId()).orElseThrow();
			assertThat(reloaded.getItems().get(0).getStatus()).isEqualTo(OrderItemStatus.CANCELED);
			assertThat(reloaded.getStatus()).isEqualTo(OrderStatus.CANCELED);
			assertRestored(purchased);
		}
	}

	private static void sleepQuietly(long millis) {
		try {
			Thread.sleep(millis);
		} catch (InterruptedException e) {
			Thread.currentThread().interrupt();
		}
	}

	private record Purchased(Long memberId, Long orderId) {
	}

	private record RaceResult<T>(int decreasedRows, T restored) {
	}
}
