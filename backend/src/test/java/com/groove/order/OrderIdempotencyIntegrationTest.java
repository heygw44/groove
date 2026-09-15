package com.groove.order;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

import com.groove.cart.entity.Cart;
import com.groove.cart.entity.CartItem;
import com.groove.cart.repository.CartItemRepository;
import com.groove.cart.repository.CartRepository;
import com.groove.fixture.AddressFixture;
import com.groove.fixture.ArtistFixture;
import com.groove.fixture.CartFixture;
import com.groove.fixture.MemberFixture;
import com.groove.fixture.OrderFixture;
import com.groove.fixture.ProductFixture;
import com.groove.fixture.StockFixture;
import com.groove.global.common.BusinessException;
import com.groove.global.common.ErrorCode;
import com.groove.global.idempotency.IdempotentResult;
import com.groove.inventory.entity.Stock;
import com.groove.inventory.repository.StockRepository;
import com.groove.member.entity.Address;
import com.groove.member.entity.Member;
import com.groove.member.repository.AddressRepository;
import com.groove.member.repository.MemberRepository;
import com.groove.order.dto.OrderCreateRequest;
import com.groove.order.dto.OrderCreateResponse;
import com.groove.order.repository.OrderRepository;
import com.groove.order.service.OrderCreateService;
import com.groove.product.entity.Artist;
import com.groove.product.entity.Product;
import com.groove.product.repository.AlbumRepository;
import com.groove.product.repository.ArtistRepository;
import com.groove.product.repository.ProductRepository;
import com.groove.support.IntegrationTestSupport;

class OrderIdempotencyIntegrationTest extends IntegrationTestSupport {

	private static final int CONCURRENT_REQUEST_COUNT = 10;

	@Autowired
	private ArtistRepository artistRepository;

	@Autowired
	private AlbumRepository albumRepository;

	@Autowired
	private ProductRepository productRepository;

	@Autowired
	private StockRepository stockRepository;

	@Autowired
	private MemberRepository memberRepository;

	@Autowired
	private AddressRepository addressRepository;

	@Autowired
	private CartRepository cartRepository;

	@Autowired
	private CartItemRepository cartItemRepository;

	@Autowired
	private OrderRepository orderRepository;

	@Autowired
	private OrderCreateService orderCreateService;

	private Product createProduct(int stockQuantity) {
		Artist artist = artistRepository.save(ArtistFixture.create());
		Product createdProduct = ProductFixture.create(artist);
		albumRepository.save(createdProduct.getAlbum());
		Product product = productRepository.save(createdProduct);
		stockRepository.saveAndFlush(StockFixture.create(product, stockQuantity));
		return product;
	}

	private Member createMember() {
		return memberRepository.save(MemberFixture.create("buyer-" + UUID.randomUUID() + "@groove.com"));
	}

	private Address createAddress(Member member) {
		return addressRepository.save(AddressFixture.create(member));
	}

	private long countOrdersOf(Long memberId) {
		return orderRepository.findAll().stream()
				.filter(order -> order.getMember().getId().equals(memberId))
				.count();
	}

	@Nested
	@DisplayName("create()")
	class Create {

		@Test
		@DisplayName("같은 키로 동시에 10건 요청하면 주문은 1건만 생성되고 재고도 한 번만 차감된다")
		void createsOnlyOneOrderForConcurrentSameKeyRequests() throws InterruptedException {
			// given
			Product product = createProduct(5);
			Member member = createMember();
			Address address = createAddress(member);
			String key = UUID.randomUUID().toString();
			OrderCreateRequest request = OrderFixture.directRequest(product.getId(), 1, address.getId());

			ExecutorService executorService = Executors.newFixedThreadPool(CONCURRENT_REQUEST_COUNT);
			CountDownLatch readyLatch = new CountDownLatch(CONCURRENT_REQUEST_COUNT);
			CountDownLatch startLatch = new CountDownLatch(1);
			List<AtomicReference<IdempotentResult<OrderCreateResponse>>> results = new ArrayList<>();
			List<AtomicReference<Throwable>> failures = new ArrayList<>();

			// when
			for (int i = 0; i < CONCURRENT_REQUEST_COUNT; i++) {
				AtomicReference<IdempotentResult<OrderCreateResponse>> result = new AtomicReference<>();
				AtomicReference<Throwable> failure = new AtomicReference<>();
				results.add(result);
				failures.add(failure);
				executorService.submit(() -> {
					try {
						readyLatch.countDown();
						startLatch.await();
						result.set(orderCreateService.create(member.getId(), key, request));
					} catch (Throwable throwable) {
						failure.set(throwable);
					}
				});
			}
			readyLatch.await();
			startLatch.countDown();
			executorService.shutdown();
			boolean finished = executorService.awaitTermination(30, TimeUnit.SECONDS);

			// then
			assertThat(finished).isTrue();
			List<Long> orderIds = results.stream()
					.map(AtomicReference::get)
					.filter(Objects::nonNull)
					.map(result -> result.response().orderId())
					.distinct()
					.toList();
			assertThat(orderIds).hasSize(1);
			failures.stream()
					.map(AtomicReference::get)
					.filter(Objects::nonNull)
					.forEach(throwable -> assertThat(throwable)
							.isInstanceOf(BusinessException.class)
							.extracting("errorCode").isEqualTo(ErrorCode.ORDER_REQUEST_IN_PROGRESS));
			Stock stock = stockRepository.findByProductId(product.getId()).orElseThrow();
			assertThat(stock.getQuantity()).isEqualTo(4);
			assertThat(countOrdersOf(member.getId())).isEqualTo(1);
		}

		@Test
		@DisplayName("완료된 요청을 같은 키로 재요청하면 같은 orderId 를 replayed 로 반환한다")
		void replaysCompletedResponseForSameKey() {
			// given
			Product product = createProduct(5);
			Member member = createMember();
			Address address = createAddress(member);
			String key = UUID.randomUUID().toString();
			OrderCreateRequest request = OrderFixture.directRequest(product.getId(), 1, address.getId());

			// when
			IdempotentResult<OrderCreateResponse> first = orderCreateService.create(member.getId(), key, request);
			IdempotentResult<OrderCreateResponse> second = orderCreateService.create(member.getId(), key, request);

			// then
			assertThat(first.replayed()).isFalse();
			assertThat(second.replayed()).isTrue();
			assertThat(second.response().orderId()).isEqualTo(first.response().orderId());
			assertThat(countOrdersOf(member.getId())).isEqualTo(1);
		}

		@Test
		@DisplayName("재고 부족으로 실패한 뒤 재고를 채우고 같은 키로 재시도하면 성공한다")
		void succeedsOnRetryAfterStockReplenished() {
			// given
			Product product = createProduct(0);
			Member member = createMember();
			Address address = createAddress(member);
			String key = UUID.randomUUID().toString();
			OrderCreateRequest request = OrderFixture.directRequest(product.getId(), 1, address.getId());

			// when & then
			assertThatThrownBy(() -> orderCreateService.create(member.getId(), key, request))
					.isInstanceOf(BusinessException.class)
					.extracting("errorCode").isEqualTo(ErrorCode.STOCK_INSUFFICIENT);
			assertThat(countOrdersOf(member.getId())).isZero();

			Stock stock = stockRepository.findWithProductByProductId(product.getId()).orElseThrow();
			stock.increase(1);
			stockRepository.saveAndFlush(stock);

			IdempotentResult<OrderCreateResponse> retried = orderCreateService.create(member.getId(), key, request);

			assertThat(retried.replayed()).isFalse();
			assertThat(countOrdersOf(member.getId())).isEqualTo(1);
		}

		@Test
		@DisplayName("같은 키에 다른 요청 바디가 오면 IDEMPOTENCY_KEY_REUSED 예외를 던진다")
		void throwsReusedWhenBodyDiffersForSameKey() {
			// given
			Product product = createProduct(5);
			Member member = createMember();
			Address address = createAddress(member);
			String key = UUID.randomUUID().toString();
			OrderCreateRequest firstRequest = OrderFixture.directRequest(product.getId(), 1, address.getId());
			OrderCreateRequest secondRequest = OrderFixture.directRequest(product.getId(), 2, address.getId());
			orderCreateService.create(member.getId(), key, firstRequest);

			// when & then
			assertThatThrownBy(() -> orderCreateService.create(member.getId(), key, secondRequest))
					.isInstanceOf(BusinessException.class)
					.extracting("errorCode").isEqualTo(ErrorCode.IDEMPOTENCY_KEY_REUSED);
			assertThat(countOrdersOf(member.getId())).isEqualTo(1);
		}

		@Test
		@DisplayName("키 없는 요청을 두 번 보내면 주문이 각각 생성된다")
		void createsSeparateOrdersWithoutKey() {
			// given
			Product product = createProduct(5);
			Member member = createMember();
			Address address = createAddress(member);
			OrderCreateRequest request = OrderFixture.directRequest(product.getId(), 1, address.getId());

			// when
			IdempotentResult<OrderCreateResponse> first = orderCreateService.create(member.getId(), null, request);
			IdempotentResult<OrderCreateResponse> second = orderCreateService.create(member.getId(), null, request);

			// then
			assertThat(first.response().orderId()).isNotEqualTo(second.response().orderId());
			assertThat(countOrdersOf(member.getId())).isEqualTo(2);
		}

		@Test
		@DisplayName("장바구니 주문도 같은 키로 재요청하면 항목이 삭제된 뒤에도 replayed 로 같은 응답을 반환한다")
		void replaysCartOrderEvenAfterCartItemsDeleted() {
			// given
			Product product = createProduct(5);
			Member member = createMember();
			Address address = createAddress(member);
			Cart cart = cartRepository.save(CartFixture.createCart(member));
			CartItem cartItem = cartItemRepository.save(CartFixture.createItem(cart, product, 1));
			String key = UUID.randomUUID().toString();
			OrderCreateRequest request = OrderFixture.cartRequest(List.of(cartItem.getId()), address.getId());

			// when
			IdempotentResult<OrderCreateResponse> first = orderCreateService.create(member.getId(), key, request);

			// then
			assertThat(cartItemRepository.findById(cartItem.getId())).isEmpty();

			// when
			IdempotentResult<OrderCreateResponse> second = orderCreateService.create(member.getId(), key, request);

			// then
			assertThat(second.replayed()).isTrue();
			assertThat(second.response().orderId()).isEqualTo(first.response().orderId());
		}
	}
}
