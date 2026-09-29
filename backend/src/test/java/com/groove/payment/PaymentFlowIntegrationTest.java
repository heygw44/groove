package com.groove.payment;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.hamcrest.Matchers.is;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.willThrow;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.reset;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.math.BigDecimal;
import java.sql.Timestamp;
import java.time.Clock;
import java.time.LocalDateTime;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.data.domain.Limit;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.groove.admin.entity.AdminAuditAction;
import com.groove.admin.entity.AdminAuditLog;
import com.groove.admin.entity.AdminAuditTargetType;
import com.groove.admin.repository.AdminAuditLogRepository;
import com.groove.auth.dto.LoginRequest;
import com.groove.auth.dto.SignupRequest;
import com.groove.auth.jwt.JwtProvider;
import com.groove.coupon.dto.CouponIssueRequest;
import com.groove.coupon.entity.Coupon;
import com.groove.coupon.entity.DiscountType;
import com.groove.coupon.entity.MemberCoupon;
import com.groove.coupon.repository.CouponRepository;
import com.groove.coupon.repository.MemberCouponRepository;
import com.groove.fixture.AddressFixture;
import com.groove.fixture.ArtistFixture;
import com.groove.fixture.LimitedDropFixture;
import com.groove.fixture.OrderFixture;
import com.groove.fixture.PaymentFixture;
import com.groove.fixture.ProductFixture;
import com.groove.fixture.StockFixture;
import com.groove.global.common.BusinessException;
import com.groove.global.common.ErrorCode;
import com.groove.inventory.entity.Stock;
import com.groove.inventory.entity.StockChangeType;
import com.groove.inventory.entity.StockHistory;
import com.groove.inventory.repository.StockHistoryRepository;
import com.groove.inventory.repository.StockRepository;
import com.groove.limited.dto.LimitedPurchaseResponse;
import com.groove.limited.entity.LimitedDrop;
import com.groove.limited.entity.LimitedDropStatus;
import com.groove.limited.repository.LimitedDropRepository;
import com.groove.limited.repository.LimitedPurchaseRepository;
import com.groove.limited.service.LimitedDropRedisService;
import com.groove.limited.service.LimitedPurchaseService;
import com.groove.member.entity.Address;
import com.groove.member.entity.Member;
import com.groove.member.entity.MemberRole;
import com.groove.member.repository.AddressRepository;
import com.groove.member.repository.MemberRepository;
import com.groove.order.dto.AdminOrderClaimCompleteRequest;
import com.groove.order.dto.AdminOrderClaimRejectRequest;
import com.groove.order.dto.AdminOrderStatusChangeRequest;
import com.groove.order.dto.OrderCancelRequest;
import com.groove.order.dto.OrderCreateRequest;
import com.groove.order.dto.OrderReturnRequest;
import com.groove.order.entity.Order;
import com.groove.order.entity.OrderClaim;
import com.groove.order.entity.OrderClaimStatus;
import com.groove.order.entity.OrderItem;
import com.groove.order.entity.OrderItemClaimStatus;
import com.groove.order.entity.OrderItemStatus;
import com.groove.order.entity.OrderStatus;
import com.groove.order.repository.OrderClaimRepository;
import com.groove.order.repository.OrderItemRepository;
import com.groove.order.repository.OrderRepository;
import com.groove.order.scheduler.OrderExpirationScheduler;
import com.groove.order.service.OrderClaimFinalizeService;
import com.groove.payment.client.PaymentClient;
import com.groove.payment.client.dto.PaymentCancelCommand;
import com.groove.payment.client.dto.PaymentCancelResult;
import com.groove.payment.client.dto.PaymentConfirmResult;
import com.groove.payment.client.dto.PaymentLookupResult;
import com.groove.payment.client.dto.PaymentLookupStatus;
import com.groove.payment.dto.PaymentCancelRequest;
import com.groove.payment.dto.PaymentConfirmRequest;
import com.groove.payment.dto.PaymentReconcileCandidate;
import com.groove.payment.entity.Payment;
import com.groove.payment.entity.PaymentCancel;
import com.groove.payment.entity.PaymentCancelStatus;
import com.groove.payment.entity.PaymentStatus;
import com.groove.payment.repository.PaymentCancelRepository;
import com.groove.payment.repository.PaymentRepository;
import com.groove.payment.scheduler.PaymentReconcileScheduler;
import com.groove.payment.service.PaymentRefundResult;
import com.groove.payment.service.PaymentRefundService;
import com.groove.payment.service.PaymentRefundStatus;
import com.groove.product.entity.Artist;
import com.groove.product.entity.Product;
import com.groove.product.repository.AlbumRepository;
import com.groove.product.repository.ArtistRepository;
import com.groove.product.repository.ProductRepository;
import com.groove.support.IntegrationTestSupport;

@AutoConfigureMockMvc
class PaymentFlowIntegrationTest extends IntegrationTestSupport {

	@Autowired
	MockMvc mockMvc;

	@Autowired
	ObjectMapper objectMapper;

	@Autowired
	MemberRepository memberRepository;

	@Autowired
	AddressRepository addressRepository;

	@Autowired
	ArtistRepository artistRepository;

	@Autowired
	ProductRepository productRepository;

	@Autowired
	AlbumRepository albumRepository;

	@Autowired
	StockRepository stockRepository;

	@Autowired
	OrderRepository orderRepository;

	@Autowired
	PaymentRepository paymentRepository;

	@Autowired
	LimitedDropRepository limitedDropRepository;

	@Autowired
	LimitedPurchaseRepository limitedPurchaseRepository;

	@Autowired
	LimitedDropRedisService limitedDropRedisService;

	@Autowired
	LimitedPurchaseService limitedPurchaseService;

	@Autowired
	OrderExpirationScheduler orderExpirationScheduler;

	@Autowired
	StockHistoryRepository stockHistoryRepository;

	@Autowired
	CouponRepository couponRepository;

	@Autowired
	MemberCouponRepository memberCouponRepository;

	@Autowired
	StringRedisTemplate redisTemplate;

	@Autowired
	Clock clock;

	@Autowired
	JwtProvider jwtProvider;

	@Autowired
	AdminAuditLogRepository adminAuditLogRepository;

	@Autowired
	PlatformTransactionManager transactionManager;

	@Autowired
	PaymentReconcileScheduler paymentReconcileScheduler;

	@Autowired
	JdbcTemplate jdbcTemplate;

	@Autowired
	PaymentRefundService paymentRefundService;

	@Autowired
	PaymentCancelRepository paymentCancelRepository;

	@Autowired
	OrderClaimRepository orderClaimRepository;

	@Autowired
	OrderItemRepository orderItemRepository;

	@Autowired
	OrderClaimFinalizeService orderClaimFinalizeService;

	@MockitoBean
	PaymentClient paymentClient;

	@Nested
	@DisplayName("POST /api/v1/payments/confirm")
	class Confirm {

		@Test
		@DisplayName("클라이언트가 금액을 조작하면 토스를 호출하지 않고 400 을 반환하며 주문은 PENDING 으로 남는다")
		void rejectsTamperedAmountWithoutCallingToss() throws Exception {
			// given
			Member member = signup();
			String accessToken = login(member.getEmail());
			Address address = addressRepository.save(AddressFixture.create(member));
			Product product = seedProduct(5);
			OrderInfo orderInfo = createOrder(accessToken, product.getId(), 1, address.getId());
			String paymentKey = uniquePaymentKey();

			// when & then: 실제 결제 금액보다 적은 금액으로 승인 요청을 보낸다
			mockMvc.perform(post("/api/v1/payments/confirm")
							.header(HttpHeaders.AUTHORIZATION, "Bearer " + accessToken)
							.contentType(MediaType.APPLICATION_JSON)
							.content(objectMapper.writeValueAsString(
									confirmRequest(paymentKey, orderInfo.orderNumber(), 1L))))
					.andExpect(status().isBadRequest())
					.andExpect(jsonPath("$.error.code", is("ORDER_AMOUNT_MISMATCH")));

			verify(paymentClient, never()).confirm(any(), any(), any());
			Order order = orderRepository.findById(orderInfo.orderId()).orElseThrow();
			assertThat(order.getStatus()).isEqualTo(OrderStatus.PENDING);
		}

		@Test
		@DisplayName("같은 결제 키로 두 번 요청해도 토스는 한 번만 호출되고 같은 결제 1건만 남는다")
		void confirmsIdempotentlyForSamePaymentKey() throws Exception {
			// given
			Member member = signup();
			String accessToken = login(member.getEmail());
			Address address = addressRepository.save(AddressFixture.create(member));
			Product product = seedProduct(5);
			OrderInfo orderInfo = createOrder(accessToken, product.getId(), 1, address.getId());
			String paymentKey = uniquePaymentKey();
			stubConfirmSuccess(paymentKey, orderInfo.orderNumber(), orderInfo.finalAmount());

			// when
			MvcResult first = confirm(accessToken, paymentKey, orderInfo).andExpect(status().isOk()).andReturn();
			MvcResult second = confirm(accessToken, paymentKey, orderInfo).andExpect(status().isOk()).andReturn();

			// then: timestamp 를 제외한 data 는 완전히 같다
			JsonNode firstData = objectMapper.readTree(first.getResponse().getContentAsString()).path("data");
			JsonNode secondData = objectMapper.readTree(second.getResponse().getContentAsString()).path("data");
			assertThat(firstData).isEqualTo(secondData);
			verify(paymentClient, times(1)).confirm(eq(paymentKey), eq(orderInfo.orderNumber()), any());
			Payment payment = paymentRepository.findByOrderId(orderInfo.orderId()).orElseThrow();
			assertThat(payment.getStatus()).isEqualTo(PaymentStatus.DONE);
			Order order = orderRepository.findById(orderInfo.orderId()).orElseThrow();
			assertThat(order.getStatus()).isEqualTo(OrderStatus.PAID);
		}

		@Test
		@DisplayName("토스 승인이 실패하면 주문은 PENDING 유지, 결제는 FAILED 로 남고 재시도하면 승인된다")
		void keepsOrderPendingOnFailureAndSucceedsOnRetry() throws Exception {
			// given
			Member member = signup();
			String accessToken = login(member.getEmail());
			Address address = addressRepository.save(AddressFixture.create(member));
			Product product = seedProduct(5);
			OrderInfo orderInfo = createOrder(accessToken, product.getId(), 1, address.getId());
			String paymentKey = uniquePaymentKey();
			willThrow(new BusinessException(ErrorCode.PAYMENT_CONFIRM_FAILED, "TOSS REJECT_CARD_COMPANY"))
					.given(paymentClient).confirm(eq(paymentKey), eq(orderInfo.orderNumber()), any());

			// when & then: 첫 요청은 토스 승인 실패로 400 을 반환한다
			confirm(accessToken, paymentKey, orderInfo)
					.andExpect(status().isBadRequest())
					.andExpect(jsonPath("$.error.code", is("PAYMENT_CONFIRM_FAILED")));

			Order orderAfterFailure = orderRepository.findById(orderInfo.orderId()).orElseThrow();
			assertThat(orderAfterFailure.getStatus()).isEqualTo(OrderStatus.PENDING);
			Payment failedPayment = paymentRepository.findByOrderId(orderInfo.orderId()).orElseThrow();
			assertThat(failedPayment.getStatus()).isEqualTo(PaymentStatus.FAILED);
			assertThat(failedPayment.getFailReason()).contains("TOSS REJECT_CARD_COMPANY");

			// when: 재시도하면 토스가 성공 응답을 준다
			reset(paymentClient);
			stubConfirmSuccess(paymentKey, orderInfo.orderNumber(), orderInfo.finalAmount());

			// then: 재시도는 성공하고 같은 결제 행이 DONE 으로 바뀐다
			confirm(accessToken, paymentKey, orderInfo).andExpect(status().isOk());
			Payment donePayment = paymentRepository.findByOrderId(orderInfo.orderId()).orElseThrow();
			assertThat(donePayment.getId()).isEqualTo(failedPayment.getId());
			assertThat(donePayment.getStatus()).isEqualTo(PaymentStatus.DONE);
			Order orderAfterRetry = orderRepository.findById(orderInfo.orderId()).orElseThrow();
			assertThat(orderAfterRetry.getStatus()).isEqualTo(OrderStatus.PAID);
		}

		@Test
		@DisplayName("한정반 주문을 승인한 뒤 만료 스케줄러를 돌려도 PAID 상태가 유지된다")
		void keepsLimitedOrderPaidAfterExpirationSchedulerRuns() throws Exception {
			// given: 한정반 구매는 회원가입 흐름으로 만들어야 로그인용 비밀번호 해시가 실제로 유효하다
			Long dropId = prepareOpenDrop(5);
			Member member = signup();
			Address address = addressRepository.save(AddressFixture.create(member));
			LimitedPurchaseResponse purchase = limitedPurchaseService.purchase(dropId, member.getId(),
					address.getId());
			String accessToken = login(member.getEmail());
			String paymentKey = uniquePaymentKey();
			stubConfirmSuccess(paymentKey, purchase.orderNumber(), purchase.finalAmount());

			// when: 결제를 승인한다
			mockMvc.perform(post("/api/v1/payments/confirm")
							.header(HttpHeaders.AUTHORIZATION, "Bearer " + accessToken)
							.contentType(MediaType.APPLICATION_JSON)
							.content(objectMapper.writeValueAsString(confirmRequest(paymentKey,
									purchase.orderNumber(), purchase.finalAmount().longValueExact()))))
					.andExpect(status().isOk())
					.andExpect(jsonPath("$.data.status", is("DONE")));

			// then: 결제 기한을 지나 만료 스케줄러가 돌아도 PAID 는 유지된다
			Order order = orderRepository.findById(purchase.orderId()).orElseThrow();
			OrderFixture.withExpiresAt(order, LocalDateTime.now(clock).minusMinutes(1));
			orderRepository.saveAndFlush(order);
			orderExpirationScheduler.expireOrders();

			Order reloadedOrder = orderRepository.findById(purchase.orderId()).orElseThrow();
			assertThat(reloadedOrder.getStatus()).isEqualTo(OrderStatus.PAID);
			limitedDropRedisService.clear(dropId);
		}
	}

	@Nested
	@DisplayName("POST /api/v1/payments/{id}/cancel")
	class Cancel {

		@Test
		@DisplayName("토스 취소 응답을 기다리는 동안에도 같은 상품 재고 갱신이 곧바로 끝난다")
		void doesNotHoldStockLockWhileWaitingForTossCancel() throws Exception {
			// given
			Member member = signup();
			String accessToken = login(member.getEmail());
			Address address = addressRepository.save(AddressFixture.create(member));
			Product product = seedProduct(5);
			OrderInfo orderInfo = createOrder(accessToken, product.getId(), 1, address.getId());
			String paymentKey = uniquePaymentKey();
			long paymentId = confirmAndGetPaymentId(accessToken, paymentKey, orderInfo.orderNumber(),
					orderInfo.finalAmount());
			CountDownLatch tossCallStarted = new CountDownLatch(1);
			CountDownLatch releaseTossCall = new CountDownLatch(1);
			LocalDateTime canceledAt = LocalDateTime.now(clock).truncatedTo(ChronoUnit.SECONDS);
			given(paymentClient.cancel(eq(paymentKey), any(), any())).willAnswer(invocation -> {
				tossCallStarted.countDown();
				releaseTossCall.await(10, TimeUnit.SECONDS);
				return PaymentCancelResult.of(paymentKey, "CANCELED", canceledAt);
			});

			CompletableFuture<Void> cancelRequest = CompletableFuture.runAsync(() -> {
				try {
					mockMvc.perform(post("/api/v1/payments/" + paymentId + "/cancel")
							.header(HttpHeaders.AUTHORIZATION, "Bearer " + accessToken)
							.contentType(MediaType.APPLICATION_JSON)
							.content(objectMapper.writeValueAsString(new PaymentCancelRequest("고객 변심"))))
						.andExpect(status().isOk());
				} catch (Exception e) {
					throw new IllegalStateException(e);
				}
			});
			assertThat(tossCallStarted.await(10, TimeUnit.SECONDS)).isTrue();

			try {
				// when
				TransactionTemplate transactionTemplate = new TransactionTemplate(transactionManager);
				CompletableFuture<Void> stockUpdate = CompletableFuture.runAsync(() ->
						transactionTemplate.executeWithoutResult(status -> {
							Stock stock = stockRepository.findAllWithProductByProductIdInForUpdate(
									List.of(product.getId())).get(0);
							stock.increase(1);
							stockRepository.flush();
						}));

				// then
				assertThatCode(() -> stockUpdate.get(2, TimeUnit.SECONDS)).doesNotThrowAnyException();
			} finally {
				releaseTossCall.countDown();
				cancelRequest.get(10, TimeUnit.SECONDS);
			}
		}

		@Test
		@DisplayName("쿠폰이 적용된 결제 완료 주문을 취소하면 결제/주문/재고/쿠폰이 모두 복구된다")
		void cancelsPaidOrderWithCouponAndRestoresEverything() throws Exception {
			// given
			Member member = signup();
			String accessToken = login(member.getEmail());
			Address address = addressRepository.save(AddressFixture.create(member));
			Product product = seedProduct(5);
			CouponOrderInfo orderInfo = createOrderWithCoupon(accessToken, product.getId(), 1, address.getId());
			String paymentKey = uniquePaymentKey();
			long paymentId = confirmAndGetPaymentId(accessToken, paymentKey, orderInfo.orderNumber(),
					orderInfo.finalAmount());
			String reason = "고객 변심";
			stubCancelSuccess(paymentKey);

			// when
			mockMvc.perform(post("/api/v1/payments/" + paymentId + "/cancel")
							.header(HttpHeaders.AUTHORIZATION, "Bearer " + accessToken)
							.contentType(MediaType.APPLICATION_JSON)
							.content(objectMapper.writeValueAsString(new PaymentCancelRequest(reason))))
					.andExpect(status().isOk())
					.andExpect(jsonPath("$.data.status", is("CANCELED")));

			// then
			Payment payment = paymentRepository.findById(paymentId).orElseThrow();
			assertThat(payment.getStatus()).isEqualTo(PaymentStatus.CANCELED);
			Order order = orderRepository.findById(orderInfo.orderId()).orElseThrow();
			assertThat(order.getStatus()).isEqualTo(OrderStatus.CANCELED);
			assertThat(order.getCancelReason()).isEqualTo(reason);
			Stock stock = stockRepository.findByProductId(product.getId()).orElseThrow();
			assertThat(stock.getQuantity()).isEqualTo(5);
			List<StockHistory> histories = stockHistoryRepository.findAllByStockIdOrderByCreatedAtAsc(stock.getId());
			assertThat(histories).hasSize(2);
			assertThat(histories.get(1).getChangeType()).isEqualTo(StockChangeType.CANCEL);
			MemberCoupon memberCoupon = memberCouponRepository.findById(orderInfo.memberCouponId()).orElseThrow();
			assertThat(memberCoupon.isUsed()).isFalse();
			verify(paymentClient).cancel(eq(paymentKey), eq(reason), any());
		}

		@Test
		@DisplayName("한정반 주문을 취소하면 선점이 되돌아가 Redis 재고와 구매자 목록이 복구되고 품절 상태도 풀린다")
		void cancelsPaidLimitedOrderAndRestoresRedisReservation() throws Exception {
			// given: 총 수량 1건짜리 드롭이라 이 구매 한 건으로 SOLD_OUT 이 된다
			int totalQuantity = 1;
			Long dropId = prepareOpenDrop(totalQuantity);
			Member member = signup();
			Address address = addressRepository.save(AddressFixture.create(member));
			LimitedPurchaseResponse purchase = limitedPurchaseService.purchase(dropId, member.getId(),
					address.getId());
			String accessToken = login(member.getEmail());
			String paymentKey = uniquePaymentKey();
			long paymentId = confirmAndGetPaymentId(accessToken, paymentKey, purchase.orderNumber(),
					purchase.finalAmount());
			stubCancelSuccess(paymentKey);
			assertThat(limitedDropRepository.findById(dropId).orElseThrow().getStatus())
					.isEqualTo(LimitedDropStatus.SOLD_OUT);

			// when
			mockMvc.perform(post("/api/v1/payments/" + paymentId + "/cancel")
							.header(HttpHeaders.AUTHORIZATION, "Bearer " + accessToken)
							.contentType(MediaType.APPLICATION_JSON)
							.content(objectMapper.writeValueAsString(new PaymentCancelRequest("고객 변심"))))
					.andExpect(status().isOk());

			// then
			assertThat(limitedPurchaseRepository.findByOrderId(purchase.orderId())).isEmpty();
			LimitedDrop reloadedDrop = limitedDropRepository.findById(dropId).orElseThrow();
			assertThat(reloadedDrop.getSoldCount()).isZero();
			assertThat(reloadedDrop.getStatus()).isEqualTo(LimitedDropStatus.OPEN);
			assertThat(redisTemplate.opsForValue().get(LimitedDropRedisService.STOCK_KEY_PREFIX + dropId))
					.isEqualTo(String.valueOf(totalQuantity));
			assertThat(redisTemplate.opsForSet().isMember(LimitedDropRedisService.BUYERS_KEY_PREFIX + dropId,
					String.valueOf(member.getId()))).isFalse();

			limitedDropRedisService.clear(dropId);
		}

		@Test
		@DisplayName("토스 취소가 실패하면 400 을 반환하고 결제/주문/재고/쿠폰이 그대로 유지된다")
		void keepsEverythingWhenTossCancelFails() throws Exception {
			// given
			Member member = signup();
			String accessToken = login(member.getEmail());
			Address address = addressRepository.save(AddressFixture.create(member));
			Product product = seedProduct(5);
			CouponOrderInfo orderInfo = createOrderWithCoupon(accessToken, product.getId(), 1, address.getId());
			String paymentKey = uniquePaymentKey();
			long paymentId = confirmAndGetPaymentId(accessToken, paymentKey, orderInfo.orderNumber(),
					orderInfo.finalAmount());
			willThrow(new BusinessException(ErrorCode.PAYMENT_CANCEL_FAILED, "TOSS ALREADY_CANCELED_PAYMENT"))
					.given(paymentClient).cancel(eq(paymentKey), any(), any());

			// when & then
			mockMvc.perform(post("/api/v1/payments/" + paymentId + "/cancel")
							.header(HttpHeaders.AUTHORIZATION, "Bearer " + accessToken)
							.contentType(MediaType.APPLICATION_JSON)
							.content(objectMapper.writeValueAsString(new PaymentCancelRequest("고객 변심"))))
					.andExpect(status().isBadRequest())
					.andExpect(jsonPath("$.error.code", is("PAYMENT_CANCEL_FAILED")));

			Payment payment = paymentRepository.findById(paymentId).orElseThrow();
			assertThat(payment.getStatus()).isEqualTo(PaymentStatus.DONE);
			Order order = orderRepository.findById(orderInfo.orderId()).orElseThrow();
			assertThat(order.getStatus()).isEqualTo(OrderStatus.PAID);
			assertThat(order.getCancelReason()).isNull();
			Stock stock = stockRepository.findByProductId(product.getId()).orElseThrow();
			assertThat(stock.getQuantity()).isEqualTo(4);
			assertThat(stockHistoryRepository.findAllByStockIdOrderByCreatedAtAsc(stock.getId()))
					.extracting(StockHistory::getChangeType)
					.doesNotContain(StockChangeType.CANCEL);
			MemberCoupon memberCoupon = memberCouponRepository.findById(orderInfo.memberCouponId()).orElseThrow();
			assertThat(memberCoupon.isUsed()).isTrue();
		}

		@Test
		@DisplayName("토스 취소 결과를 알 수 없으면 주문은 PAID 이고 결제는 CANCEL_REQUESTED 로 남는다")
		void keepsCancelRequestedWhenTossResultIsUnknown() throws Exception {
			// given
			Member member = signup();
			String accessToken = login(member.getEmail());
			Address address = addressRepository.save(AddressFixture.create(member));
			Product product = seedProduct(5);
			OrderInfo orderInfo = createOrder(accessToken, product.getId(), 1, address.getId());
			String paymentKey = uniquePaymentKey();
			long paymentId = confirmAndGetPaymentId(accessToken, paymentKey, orderInfo.orderNumber(),
					orderInfo.finalAmount());
			willThrow(new BusinessException(ErrorCode.PAYMENT_RESULT_UNKNOWN))
					.given(paymentClient).cancel(eq(paymentKey), any(), any());

			// when
			mockMvc.perform(post("/api/v1/payments/" + paymentId + "/cancel")
						.header(HttpHeaders.AUTHORIZATION, "Bearer " + accessToken)
						.contentType(MediaType.APPLICATION_JSON)
						.content(objectMapper.writeValueAsString(new PaymentCancelRequest("고객 변심"))))
					.andExpect(status().isOk())
					.andExpect(jsonPath("$.data.status", is("CANCEL_REQUESTED")));

			// then
			assertThat(paymentRepository.findById(paymentId).orElseThrow().getStatus())
					.isEqualTo(PaymentStatus.CANCEL_REQUESTED);
			Order order = orderRepository.findById(orderInfo.orderId()).orElseThrow();
			assertThat(order.getStatus()).isEqualTo(OrderStatus.PAID);
			assertThat(order.getCancelReason()).isEqualTo("고객 변심");

			// when: 대사가 조회할 수 있도록 후보 grace 를 지난 결제로 만든다
			LocalDateTime canceledAt = LocalDateTime.now(clock).truncatedTo(ChronoUnit.SECONDS);
			jdbcTemplate.update("update payment set updated_at = ? where id = ?",
					Timestamp.valueOf(LocalDateTime.now(clock).minusDays(30)), paymentId);
			given(paymentClient.lookup(eq(orderInfo.orderNumber()))).willReturn(new PaymentLookupResult(
					PaymentLookupStatus.CANCELED, paymentKey, "카드", orderInfo.finalAmount(),
					LocalDateTime.now(clock).minusMinutes(5), canceledAt));
			paymentReconcileScheduler.reconcile();

			// then: 주문·결제·재고가 CANCELED 상태로 수렴한다
			assertThat(paymentRepository.findById(paymentId).orElseThrow().getStatus())
					.isEqualTo(PaymentStatus.CANCELED);
			assertThat(orderRepository.findById(orderInfo.orderId()).orElseThrow().getStatus())
					.isEqualTo(OrderStatus.CANCELED);
			assertThat(stockRepository.findByProductId(product.getId()).orElseThrow().getQuantity()).isEqualTo(5);
		}

		@Test
		@DisplayName("토스 취소 후 T2가 실패하면 CANCEL_REQUESTED 를 유지하고 이후 T2 재실행으로 수렴한다")
		void convergesWhenCompletionFailsAfterTossCancel() throws Exception {
			// given
			Member member = signup();
			String accessToken = login(member.getEmail());
			Address address = addressRepository.save(AddressFixture.create(member));
			Product product = seedProduct(5);
			OrderInfo orderInfo = createOrder(accessToken, product.getId(), 1, address.getId());
			String paymentKey = uniquePaymentKey();
			long paymentId = confirmAndGetPaymentId(accessToken, paymentKey, orderInfo.orderNumber(),
					orderInfo.finalAmount());
			LocalDateTime canceledAt = LocalDateTime.now(clock).truncatedTo(ChronoUnit.SECONDS);
			given(paymentClient.cancel(eq(paymentKey), any(), any())).willAnswer(invocation -> {
				jdbcTemplate.update("update orders set status = 'DELIVERED' where id = ?", orderInfo.orderId());
				return PaymentCancelResult.of(paymentKey, "CANCELED", canceledAt);
			});

			// when: T2 의 주문 상태 검증을 결정적으로 실패시킨다
			mockMvc.perform(post("/api/v1/payments/" + paymentId + "/cancel")
						.header(HttpHeaders.AUTHORIZATION, "Bearer " + accessToken)
						.contentType(MediaType.APPLICATION_JSON)
						.content(objectMapper.writeValueAsString(new PaymentCancelRequest("고객 변심"))))
					.andExpect(status().isOk())
					.andExpect(jsonPath("$.data.status", is("CANCEL_REQUESTED")));

			// then: T1 은 유지되고 토스 취소는 한 번만 호출된다
			assertThat(paymentRepository.findById(paymentId).orElseThrow().getStatus())
					.isEqualTo(PaymentStatus.CANCEL_REQUESTED);
			verify(paymentClient, times(1)).cancel(eq(paymentKey), eq("고객 변심"), any());

			// when: 대사가 조회한 CANCELED 결과를 반영할 수 있도록 주문 상태를 원래대로 되돌린다
			jdbcTemplate.update("update orders set status = 'PAID' where id = ?", orderInfo.orderId());
			jdbcTemplate.update("update payment set updated_at = ? where id = ?",
					Timestamp.valueOf(LocalDateTime.now(clock).minusDays(30)), paymentId);
			given(paymentClient.lookup(eq(orderInfo.orderNumber()))).willReturn(new PaymentLookupResult(
					PaymentLookupStatus.CANCELED, paymentKey, "카드", orderInfo.finalAmount(),
					LocalDateTime.now(clock).minusMinutes(5), canceledAt));
			paymentReconcileScheduler.reconcile();

			// then
			assertThat(paymentRepository.findById(paymentId).orElseThrow().getStatus())
					.isEqualTo(PaymentStatus.CANCELED);
			assertThat(orderRepository.findById(orderInfo.orderId()).orElseThrow().getStatus())
					.isEqualTo(OrderStatus.CANCELED);
			assertThat(stockRepository.findByProductId(product.getId()).orElseThrow().getQuantity()).isEqualTo(5);
			verify(paymentClient, times(1)).cancel(eq(paymentKey), eq("고객 변심"), any());
		}

		@Test
		@DisplayName("CANCEL_REQUESTED 주문을 다시 취소하면 토스를 재호출하지 않고 같은 상태를 반환한다")
		void returnsIdempotentlyForDuplicateCancelRequest() throws Exception {
			// given
			Member member = signup();
			String accessToken = login(member.getEmail());
			Address address = addressRepository.save(AddressFixture.create(member));
			Product product = seedProduct(5);
			OrderInfo orderInfo = createOrder(accessToken, product.getId(), 1, address.getId());
			String paymentKey = uniquePaymentKey();
			long paymentId = confirmAndGetPaymentId(accessToken, paymentKey, orderInfo.orderNumber(),
					orderInfo.finalAmount());
			willThrow(new BusinessException(ErrorCode.PAYMENT_RESULT_UNKNOWN))
					.given(paymentClient).cancel(eq(paymentKey), any(), any());
			PaymentCancelRequest request = new PaymentCancelRequest("고객 변심");

			// when
			mockMvc.perform(post("/api/v1/payments/" + paymentId + "/cancel")
						.header(HttpHeaders.AUTHORIZATION, "Bearer " + accessToken)
						.contentType(MediaType.APPLICATION_JSON)
						.content(objectMapper.writeValueAsString(request)))
					.andExpect(status().isOk());
			mockMvc.perform(post("/api/v1/payments/" + paymentId + "/cancel")
						.header(HttpHeaders.AUTHORIZATION, "Bearer " + accessToken)
						.contentType(MediaType.APPLICATION_JSON)
						.content(objectMapper.writeValueAsString(request)))
					.andExpect(status().isOk())
					.andExpect(jsonPath("$.data.status", is("CANCEL_REQUESTED")));

			// then
			verify(paymentClient, times(1)).cancel(eq(paymentKey), eq("고객 변심"), any());
		}

		@Test
		@DisplayName("결제 완료 주문을 /orders/{id}/cancel 로 취소해도 결제가 취소되고 토스 취소가 호출된다")
		void cancelsPaymentThroughOrderCancelEndpoint() throws Exception {
			// given
			Member member = signup();
			String accessToken = login(member.getEmail());
			Address address = addressRepository.save(AddressFixture.create(member));
			Product product = seedProduct(5);
			OrderInfo orderInfo = createOrder(accessToken, product.getId(), 1, address.getId());
			String paymentKey = uniquePaymentKey();
			long paymentId = confirmAndGetPaymentId(accessToken, paymentKey, orderInfo.orderNumber(),
					orderInfo.finalAmount());
			given(paymentClient.cancel(eq(paymentKey), any(), any()))
					.willReturn(PaymentCancelResult.of(paymentKey, "CANCELED",
							LocalDateTime.now(clock).truncatedTo(ChronoUnit.SECONDS)));

			// when
			mockMvc.perform(post("/api/v1/orders/" + orderInfo.orderId() + "/cancel")
							.header(HttpHeaders.AUTHORIZATION, "Bearer " + accessToken))
					.andExpect(status().isOk())
					.andExpect(jsonPath("$.data.status", is("CANCELED")));

			// then
			Payment payment = paymentRepository.findById(paymentId).orElseThrow();
			assertThat(payment.getStatus()).isEqualTo(PaymentStatus.CANCELED);
			verify(paymentClient).cancel(eq(paymentKey), any(), any());
		}

		@Test
		@DisplayName("DONE 이 아닌 결제를 취소하려 하면 409 를 반환하고 토스를 호출하지 않는다")
		void returnsConflictWhenPaymentIsNotDone() throws Exception {
			// given: confirm 을 실패시켜 FAILED 결제를 만든다
			Member member = signup();
			String accessToken = login(member.getEmail());
			Address address = addressRepository.save(AddressFixture.create(member));
			Product product = seedProduct(5);
			OrderInfo orderInfo = createOrder(accessToken, product.getId(), 1, address.getId());
			String paymentKey = uniquePaymentKey();
			willThrow(new BusinessException(ErrorCode.PAYMENT_CONFIRM_FAILED, "TOSS REJECT_CARD_COMPANY"))
					.given(paymentClient).confirm(eq(paymentKey), eq(orderInfo.orderNumber()), any());
			confirm(accessToken, paymentKey, orderInfo).andExpect(status().isBadRequest());
			Payment failedPayment = paymentRepository.findByOrderId(orderInfo.orderId()).orElseThrow();
			assertThat(failedPayment.getStatus()).isEqualTo(PaymentStatus.FAILED);
			reset(paymentClient);

			// when & then
			mockMvc.perform(post("/api/v1/payments/" + failedPayment.getId() + "/cancel")
							.header(HttpHeaders.AUTHORIZATION, "Bearer " + accessToken)
							.contentType(MediaType.APPLICATION_JSON)
							.content(objectMapper.writeValueAsString(new PaymentCancelRequest("고객 변심"))))
					.andExpect(status().isConflict())
					.andExpect(jsonPath("$.error.code", is("PAYMENT_INVALID_STATUS")));
			verify(paymentClient, never()).cancel(any(), any(), any());
		}
	}

	@Nested
	@DisplayName("PaymentRefundService.refund() 부분취소")
	class Refund {

		@Test
		@DisplayName("같은 결제에 부분취소를 두 번 요청하면 서로 다른 멱등키로 두 건의 DONE payment_cancel 행이 남는다")
		void appliesTwoPartialCancelsWithDifferentIdempotencyKeys() throws Exception {
			// given
			Member member = signup();
			String accessToken = login(member.getEmail());
			Address address = addressRepository.save(AddressFixture.create(member));
			Product product = seedProduct(5);
			OrderInfo orderInfo = createOrder(accessToken, product.getId(), 1, address.getId());
			String paymentKey = uniquePaymentKey();
			long paymentId = confirmAndGetPaymentId(accessToken, paymentKey, orderInfo.orderNumber(),
					orderInfo.finalAmount());
			BigDecimal firstAmount = new BigDecimal("10000");
			BigDecimal secondAmount = new BigDecimal("15000");
			given(paymentClient.cancel(any(PaymentCancelCommand.class))).willAnswer(invocation -> {
				PaymentCancelCommand command = invocation.getArgument(0);
				LocalDateTime canceledAt = LocalDateTime.now(clock).truncatedTo(ChronoUnit.SECONDS);
				return new PaymentCancelResult(paymentKey, "PARTIAL_CANCELED", canceledAt,
						"txn-" + command.idempotencyKey(), null);
			});

			// when
			PaymentRefundResult firstResult = paymentRefundService.refund(paymentId, firstAmount, "부분 반품 1", null);
			PaymentRefundResult secondResult = paymentRefundService.refund(paymentId, secondAmount, "부분 반품 2", null);

			// then
			assertThat(firstResult.status()).isEqualTo(PaymentRefundStatus.DONE);
			assertThat(secondResult.status()).isEqualTo(PaymentRefundStatus.DONE);

			Payment payment = paymentRepository.findById(paymentId).orElseThrow();
			assertThat(payment.getStatus()).isEqualTo(PaymentStatus.PARTIAL_CANCELED);
			assertThat(payment.getCanceledAmount()).isEqualByComparingTo(firstAmount.add(secondAmount));

			List<PaymentCancel> paymentCancels = paymentCancelRepository.findByPaymentIdOrderByIdAsc(paymentId);
			assertThat(paymentCancels).hasSize(2);
			assertThat(paymentCancels).extracting(PaymentCancel::getIdempotencyKey)
					.containsExactly("cancel-" + paymentKey + "-1", "cancel-" + paymentKey + "-2");
			assertThat(paymentCancels).extracting(PaymentCancel::getStatus)
					.containsExactly(PaymentCancelStatus.DONE, PaymentCancelStatus.DONE);
			assertThat(paymentCancels).extracting(PaymentCancel::getCancelAmount)
					.usingElementComparator(BigDecimal::compareTo)
					.containsExactly(firstAmount, secondAmount);

			ArgumentCaptor<PaymentCancelCommand> captor = ArgumentCaptor.forClass(PaymentCancelCommand.class);
			verify(paymentClient, times(2)).cancel(captor.capture());
			assertThat(captor.getAllValues()).extracting(PaymentCancelCommand::idempotencyKey)
					.containsExactly("cancel-" + paymentKey + "-1", "cancel-" + paymentKey + "-2");
		}

		@Test
		@DisplayName("남은 금액을 초과해 요청하면 예외를 던지고 토스를 호출하지 않는다")
		void throwsWhenExceedsRemainingAmount() throws Exception {
			// given
			Member member = signup();
			String accessToken = login(member.getEmail());
			Address address = addressRepository.save(AddressFixture.create(member));
			Product product = seedProduct(5);
			OrderInfo orderInfo = createOrder(accessToken, product.getId(), 1, address.getId());
			String paymentKey = uniquePaymentKey();
			long paymentId = confirmAndGetPaymentId(accessToken, paymentKey, orderInfo.orderNumber(),
					orderInfo.finalAmount());
			BigDecimal tooMuch = orderInfo.finalAmount().add(BigDecimal.ONE);

			// when & then
			assertThatThrownBy(() -> paymentRefundService.refund(paymentId, tooMuch, "사유", null))
					.isInstanceOf(BusinessException.class)
					.extracting("errorCode")
					.isEqualTo(ErrorCode.PAYMENT_CANCEL_AMOUNT_EXCEEDS_BALANCE);
			verify(paymentClient, never()).cancel(any(PaymentCancelCommand.class));
		}

		@Test
		@DisplayName("부분취소 결과가 불명이면 결제는 DONE 으로 남고 대사 후보에 잡히지 않는다")
		void keepsPaymentDoneAndOutOfReconcileTargetsWhenResultIsUnknown() throws Exception {
			// given: 이전엔 이 경로가 payment.status 를 CANCEL_REQUESTED 로 옮겨, 결과불명일 때 대사
			// 스케줄러가 legacy 전액취소 키로 잘못 재시도하는 결함이 있었다
			Member member = signup();
			String accessToken = login(member.getEmail());
			Address address = addressRepository.save(AddressFixture.create(member));
			Product product = seedProduct(5);
			OrderInfo orderInfo = createOrder(accessToken, product.getId(), 1, address.getId());
			String paymentKey = uniquePaymentKey();
			long paymentId = confirmAndGetPaymentId(accessToken, paymentKey, orderInfo.orderNumber(),
					orderInfo.finalAmount());
			willThrow(new BusinessException(ErrorCode.PAYMENT_RESULT_UNKNOWN, "TOSS 통신 실패"))
					.given(paymentClient).cancel(any(PaymentCancelCommand.class));

			// when & then
			assertThatThrownBy(() -> paymentRefundService.refund(paymentId, new BigDecimal("10000"), "부분 반품", null))
					.isInstanceOf(BusinessException.class)
					.extracting("errorCode")
					.isEqualTo(ErrorCode.PAYMENT_RESULT_UNKNOWN);

			Payment payment = paymentRepository.findById(paymentId).orElseThrow();
			assertThat(payment.getStatus()).isEqualTo(PaymentStatus.DONE);

			List<PaymentCancel> paymentCancels = paymentCancelRepository.findByPaymentIdOrderByIdAsc(paymentId);
			assertThat(paymentCancels).hasSize(1);
			assertThat(paymentCancels.get(0).getStatus()).isEqualTo(PaymentCancelStatus.REQUESTED);

			List<PaymentReconcileCandidate> candidates = paymentRepository.findReconcileCandidates(
					PaymentStatus.SCHEDULED_RECONCILE_TARGETS, LocalDateTime.now(clock).plusDays(1),
					Integer.MAX_VALUE, Limit.of(1000));
			assertThat(candidates).extracting(PaymentReconcileCandidate::paymentId).doesNotContain(paymentId);
		}

		@Test
		@DisplayName("부분취소가 진행 중이면 같은 결제의 전액취소 요청은 거절된다")
		void rejectsFullCancelWhilePartialCancelInProgress() throws Exception {
			// given
			Member member = signup();
			String accessToken = login(member.getEmail());
			Address address = addressRepository.save(AddressFixture.create(member));
			Product product = seedProduct(5);
			OrderInfo orderInfo = createOrder(accessToken, product.getId(), 1, address.getId());
			String paymentKey = uniquePaymentKey();
			long paymentId = confirmAndGetPaymentId(accessToken, paymentKey, orderInfo.orderNumber(),
					orderInfo.finalAmount());
			Payment payment = paymentRepository.findById(paymentId).orElseThrow();
			paymentCancelRepository.save(PaymentCancel.request(payment, "cancel-" + paymentKey + "-1",
					new BigDecimal("10000"), "부분 반품", LocalDateTime.now(clock)));

			// when & then
			mockMvc.perform(post("/api/v1/payments/" + paymentId + "/cancel")
							.header(HttpHeaders.AUTHORIZATION, "Bearer " + accessToken)
							.contentType(MediaType.APPLICATION_JSON)
							.content(objectMapper.writeValueAsString(new PaymentCancelRequest("고객 변심"))))
					.andExpect(status().isConflict())
					.andExpect(jsonPath("$.error.code", is("PAYMENT_CANCEL_IN_PROGRESS")));
			verify(paymentClient, never()).cancel(any(), any(), any());
		}
	}

	@Nested
	@DisplayName("상품 단위 취소·반품 클레임(#540)")
	class ItemClaim {

		@Test
		@DisplayName("두 상품 중 하나만 즉시 취소하면 부분환불되고, 쿠폰은 나머지 하나까지 취소돼야 복원된다")
		void cancelsOneItemImmediatelyAndRestoresCouponOnlyWhenAllItemsAreTerminal() throws Exception {
			// given
			Member member = signup();
			addressRepository.save(AddressFixture.create(member));
			Product first = seedProduct(5);
			Product second = seedProduct(5);
			String paymentKey = uniquePaymentKey();
			TwoItemOrder order = createConfirmedTwoItemOrderWithCoupon(member, first, second, paymentKey);
			String accessToken = login(member.getEmail());
			BigDecimal firstRefundAmount = order.firstItem().getRefundableAmount();
			stubPartialCancelSuccess(paymentKey);

			// when: 첫 번째 상품만 취소한다
			mockMvc.perform(post("/api/v1/orders/{orderId}/items/{itemId}/cancel", order.orderId(),
							order.firstItem().getId())
							.header(HttpHeaders.AUTHORIZATION, "Bearer " + accessToken)
							.contentType(MediaType.APPLICATION_JSON)
							.content(objectMapper.writeValueAsString(new OrderCancelRequest("고객 변심"))))
					.andExpect(status().isOk())
					.andExpect(jsonPath("$.data.status", is("CANCELED")));

			// then: 첫 상품만 취소되고 주문은 아직 PAID, 쿠폰은 아직 사용 중이다
			Order reloadedAfterFirst = orderRepository.findById(order.orderId()).orElseThrow();
			assertThat(reloadedAfterFirst.getStatus()).isEqualTo(OrderStatus.PAID);
			assertThat(memberCouponRepository.findById(order.memberCouponId()).orElseThrow().isUsed()).isTrue();
			Payment paymentAfterFirst = paymentRepository.findByOrderId(order.orderId()).orElseThrow();
			assertThat(paymentAfterFirst.getCanceledAmount()).isEqualByComparingTo(firstRefundAmount);

			// when: 두 번째 상품도 취소한다
			BigDecimal secondRefundAmount = orderItemRepository.findById(order.secondItemId()).orElseThrow()
					.getRefundableAmount();
			mockMvc.perform(post("/api/v1/orders/{orderId}/items/{itemId}/cancel", order.orderId(),
							order.secondItemId())
							.header(HttpHeaders.AUTHORIZATION, "Bearer " + accessToken)
							.contentType(MediaType.APPLICATION_JSON)
							.content(objectMapper.writeValueAsString(new OrderCancelRequest("고객 변심"))))
					.andExpect(status().isOk());

			// then: 주문 전체가 취소되고 쿠폰이 복원된다
			Order reloadedAfterSecond = orderRepository.findById(order.orderId()).orElseThrow();
			assertThat(reloadedAfterSecond.getStatus()).isEqualTo(OrderStatus.CANCELED);
			assertThat(memberCouponRepository.findById(order.memberCouponId()).orElseThrow().isUsed()).isFalse();
			Payment paymentAfterSecond = paymentRepository.findByOrderId(order.orderId()).orElseThrow();
			assertThat(paymentAfterSecond.getCanceledAmount())
					.isEqualByComparingTo(firstRefundAmount.add(secondRefundAmount));
			assertThat(paymentAfterSecond.getStatus()).isEqualTo(PaymentStatus.CANCELED);
		}

		@Test
		@DisplayName("상품 하나를 먼저 즉시 취소한 뒤 POST /orders/{id}/cancel 을 부르면 남은 상품만 부분환불된다")
		void cancelsOrderAfterOneItemAlreadyPartiallyCanceled() throws Exception {
			// given: 첫 상품을 상품 단위 엔드포인트로 먼저 취소해 결제를 PARTIAL_CANCELED 로 만든다
			Member member = signup();
			addressRepository.save(AddressFixture.create(member));
			Product first = seedProduct(5);
			Product second = seedProduct(5);
			String paymentKey = uniquePaymentKey();
			TwoItemOrder order = createConfirmedTwoItemOrderWithCoupon(member, first, second, paymentKey);
			String accessToken = login(member.getEmail());
			BigDecimal firstRefundAmount = order.firstItem().getRefundableAmount();
			BigDecimal secondRefundAmount = orderItemRepository.findById(order.secondItemId()).orElseThrow()
					.getRefundableAmount();
			stubPartialCancelSuccess(paymentKey);
			mockMvc.perform(post("/api/v1/orders/{orderId}/items/{itemId}/cancel", order.orderId(),
							order.firstItem().getId())
							.header(HttpHeaders.AUTHORIZATION, "Bearer " + accessToken)
							.contentType(MediaType.APPLICATION_JSON)
							.content(objectMapper.writeValueAsString(new OrderCancelRequest("고객 변심"))))
					.andExpect(status().isOk());
			assertThat(paymentRepository.findByOrderId(order.orderId()).orElseThrow().getStatus())
					.isEqualTo(PaymentStatus.PARTIAL_CANCELED);
			reset(paymentClient);
			stubPartialCancelSuccess(paymentKey);

			// when: 나머지 상품을 주문 전체 취소로 정리한다
			mockMvc.perform(post("/api/v1/orders/{id}/cancel", order.orderId())
							.header(HttpHeaders.AUTHORIZATION, "Bearer " + accessToken)
							.contentType(MediaType.APPLICATION_JSON)
							.content(objectMapper.writeValueAsString(new OrderCancelRequest("고객 변심"))))
					.andExpect(status().isOk())
					.andExpect(jsonPath("$.data.status", is("CANCELED")));

			// then: 두 번째 상품 금액만큼만 토스에 취소를 요청했다(이미 취소된 첫 상품을 다시 취소하지 않는다)
			ArgumentCaptor<PaymentCancelCommand> captor = ArgumentCaptor.forClass(PaymentCancelCommand.class);
			verify(paymentClient, times(1)).cancel(captor.capture());
			assertThat(captor.getValue().cancelAmount()).isEqualByComparingTo(secondRefundAmount);

			Order reloadedOrder = orderRepository.findById(order.orderId()).orElseThrow();
			assertThat(reloadedOrder.getStatus()).isEqualTo(OrderStatus.CANCELED);
			assertThat(memberCouponRepository.findById(order.memberCouponId()).orElseThrow().isUsed()).isFalse();
			Payment finalPayment = paymentRepository.findByOrderId(order.orderId()).orElseThrow();
			assertThat(finalPayment.getStatus()).isEqualTo(PaymentStatus.CANCELED);
			assertThat(finalPayment.getCanceledAmount())
					.isEqualByComparingTo(firstRefundAmount.add(secondRefundAmount));
		}

		@Test
		@DisplayName("PREPARING 상품주문은 취소를 요청만 하고, 관리자가 승인해야 환불된다")
		void requestsCancelForPreparingItemAndAdminApprovalRefunds() throws Exception {
			// given
			Member member = signup();
			String accessToken = login(member.getEmail());
			Address address = addressRepository.save(AddressFixture.create(member));
			Product product = seedProduct(5);
			OrderInfo orderInfo = createOrder(accessToken, product.getId(), 1, address.getId());
			String paymentKey = uniquePaymentKey();
			confirmAndGetPaymentId(accessToken, paymentKey, orderInfo.orderNumber(), orderInfo.finalAmount());
			String adminToken = adminToken();
			changeAdminOrderStatus(adminToken, orderInfo.orderId(), OrderStatus.PREPARING).andExpect(status().isOk());
			Long itemId = onlyItemId(orderInfo.orderId());

			// when: 구매자가 취소를 요청한다
			mockMvc.perform(post("/api/v1/orders/{orderId}/items/{itemId}/cancel", orderInfo.orderId(), itemId)
							.header(HttpHeaders.AUTHORIZATION, "Bearer " + accessToken)
							.contentType(MediaType.APPLICATION_JSON)
							.content(objectMapper.writeValueAsString(new OrderCancelRequest("변심"))))
					.andExpect(status().isOk())
					.andExpect(jsonPath("$.data.claimStatus", is("CANCEL_REQUEST")));
			verify(paymentClient, never()).cancel(any(PaymentCancelCommand.class));
			OrderClaim claim = onlyClaimFor(itemId);
			assertThat(claim.getStatus()).isEqualTo(OrderClaimStatus.REQUESTED);

			// when: 관리자가 승인한다
			stubPartialCancelSuccess(paymentKey);
			mockMvc.perform(post("/api/v1/admin/order-claims/{id}/approve", claim.getId())
							.header(HttpHeaders.AUTHORIZATION, adminToken))
					.andExpect(status().isOk())
					.andExpect(jsonPath("$.data.status", is("CANCELED")));

			// then
			OrderItem reloadedItem = orderItemRepository.findById(itemId).orElseThrow();
			assertThat(reloadedItem.getStatus()).isEqualTo(OrderItemStatus.CANCELED);
			assertThat(reloadedItem.getClaimStatus()).isEqualTo(OrderItemClaimStatus.CANCEL_DONE);
		}

		@Test
		@DisplayName("관리자가 취소 요청을 거부하면 상품주문은 PREPARING 으로 남는다")
		void rejectsRequestAndKeepsPreparing() throws Exception {
			// given
			Member member = signup();
			String accessToken = login(member.getEmail());
			Address address = addressRepository.save(AddressFixture.create(member));
			Product product = seedProduct(5);
			OrderInfo orderInfo = createOrder(accessToken, product.getId(), 1, address.getId());
			String paymentKey = uniquePaymentKey();
			confirmAndGetPaymentId(accessToken, paymentKey, orderInfo.orderNumber(), orderInfo.finalAmount());
			String adminToken = adminToken();
			changeAdminOrderStatus(adminToken, orderInfo.orderId(), OrderStatus.PREPARING).andExpect(status().isOk());
			Long itemId = onlyItemId(orderInfo.orderId());
			mockMvc.perform(post("/api/v1/orders/{orderId}/items/{itemId}/cancel", orderInfo.orderId(), itemId)
							.header(HttpHeaders.AUTHORIZATION, "Bearer " + accessToken)
							.contentType(MediaType.APPLICATION_JSON)
							.content(objectMapper.writeValueAsString(new OrderCancelRequest("변심"))))
					.andExpect(status().isOk());
			OrderClaim claim = onlyClaimFor(itemId);

			// when
			mockMvc.perform(post("/api/v1/admin/order-claims/{id}/reject", claim.getId())
							.header(HttpHeaders.AUTHORIZATION, adminToken)
							.contentType(MediaType.APPLICATION_JSON)
							.content(objectMapper.writeValueAsString(new AdminOrderClaimRejectRequest("이미 발송 완료"))))
					.andExpect(status().isOk())
					.andExpect(jsonPath("$.data.status", is("PREPARING")))
					.andExpect(jsonPath("$.data.claimStatus", is("CANCEL_REJECT")));

			// then
			assertThat(orderClaimRepository.findById(claim.getId()).orElseThrow().getStatus())
					.isEqualTo(OrderClaimStatus.REJECTED);
			verify(paymentClient, never()).cancel(any(PaymentCancelCommand.class));
		}

		@Test
		@DisplayName("배송완료 7일 이내 반품 요청은 수거·완료(재입고 포함)를 거쳐 환불된다")
		void returnsWithinPeriodAndRestocksOnCompletion() throws Exception {
			// given
			Member member = signup();
			String accessToken = login(member.getEmail());
			Address address = addressRepository.save(AddressFixture.create(member));
			Product product = seedProduct(5);
			OrderInfo orderInfo = createOrder(accessToken, product.getId(), 1, address.getId());
			String paymentKey = uniquePaymentKey();
			confirmAndGetPaymentId(accessToken, paymentKey, orderInfo.orderNumber(), orderInfo.finalAmount());
			String adminToken = adminToken();
			changeAdminOrderStatus(adminToken, orderInfo.orderId(), OrderStatus.PREPARING).andExpect(status().isOk());
			changeAdminOrderStatus(adminToken, orderInfo.orderId(), OrderStatus.SHIPPED).andExpect(status().isOk());
			changeAdminOrderStatus(adminToken, orderInfo.orderId(), OrderStatus.DELIVERED).andExpect(status().isOk());
			Long itemId = onlyItemId(orderInfo.orderId());
			int stockBeforeReturn = stockRepository.findByProductId(product.getId()).orElseThrow().getQuantity();

			// when: 반품을 요청한다
			mockMvc.perform(post("/api/v1/orders/{orderId}/items/{itemId}/return", orderInfo.orderId(), itemId)
							.header(HttpHeaders.AUTHORIZATION, "Bearer " + accessToken)
							.contentType(MediaType.APPLICATION_JSON)
							.content(objectMapper.writeValueAsString(new OrderReturnRequest("사이즈가 안 맞음"))))
					.andExpect(status().isOk())
					.andExpect(jsonPath("$.data.claimStatus", is("RETURN_REQUEST")));
			OrderClaim claim = onlyClaimFor(itemId);

			// when: 관리자가 수거를 시작하고 재입고를 선택해 완료한다
			mockMvc.perform(post("/api/v1/admin/order-claims/{id}/collect", claim.getId())
							.header(HttpHeaders.AUTHORIZATION, adminToken))
					.andExpect(status().isOk())
					.andExpect(jsonPath("$.data.claimStatus", is("COLLECTING")));
			stubPartialCancelSuccess(paymentKey);
			mockMvc.perform(post("/api/v1/admin/order-claims/{id}/complete", claim.getId())
							.header(HttpHeaders.AUTHORIZATION, adminToken)
							.contentType(MediaType.APPLICATION_JSON)
							.content(objectMapper.writeValueAsString(new AdminOrderClaimCompleteRequest(true))))
					.andExpect(status().isOk())
					.andExpect(jsonPath("$.data.status", is("RETURNED")));

			// then
			assertThat(stockRepository.findByProductId(product.getId()).orElseThrow().getQuantity())
					.isEqualTo(stockBeforeReturn + 1);
			assertThat(orderClaimRepository.findById(claim.getId()).orElseThrow().getStatus())
					.isEqualTo(OrderClaimStatus.DONE);
		}

		@Test
		@DisplayName("배송완료 7일이 지나면 반품 요청이 거절된다")
		void rejectsReturnRequestPastReturnPeriod() throws Exception {
			// given
			Member member = signup();
			String accessToken = login(member.getEmail());
			Address address = addressRepository.save(AddressFixture.create(member));
			Product product = seedProduct(5);
			OrderInfo orderInfo = createOrder(accessToken, product.getId(), 1, address.getId());
			String paymentKey = uniquePaymentKey();
			confirmAndGetPaymentId(accessToken, paymentKey, orderInfo.orderNumber(), orderInfo.finalAmount());
			String adminToken = adminToken();
			changeAdminOrderStatus(adminToken, orderInfo.orderId(), OrderStatus.PREPARING).andExpect(status().isOk());
			changeAdminOrderStatus(adminToken, orderInfo.orderId(), OrderStatus.SHIPPED).andExpect(status().isOk());
			changeAdminOrderStatus(adminToken, orderInfo.orderId(), OrderStatus.DELIVERED).andExpect(status().isOk());
			Long itemId = onlyItemId(orderInfo.orderId());
			setDeliveredAt(itemId, LocalDateTime.now(clock).minusDays(8));

			// when & then
			mockMvc.perform(post("/api/v1/orders/{orderId}/items/{itemId}/return", orderInfo.orderId(), itemId)
							.header(HttpHeaders.AUTHORIZATION, "Bearer " + accessToken)
							.contentType(MediaType.APPLICATION_JSON)
							.content(objectMapper.writeValueAsString(new OrderReturnRequest("사유"))))
					.andExpect(status().isBadRequest())
					.andExpect(jsonPath("$.error.code", is("ORDER_RETURN_PERIOD_EXPIRED")));
		}

		@Test
		@DisplayName("본인이 요청한 클레임을 철회하면 상품주문은 클레임 이전 상태로 돌아간다")
		void withdrawsOwnClaim() throws Exception {
			// given
			Member member = signup();
			String accessToken = login(member.getEmail());
			Address address = addressRepository.save(AddressFixture.create(member));
			Product product = seedProduct(5);
			OrderInfo orderInfo = createOrder(accessToken, product.getId(), 1, address.getId());
			String paymentKey = uniquePaymentKey();
			confirmAndGetPaymentId(accessToken, paymentKey, orderInfo.orderNumber(), orderInfo.finalAmount());
			String adminToken = adminToken();
			changeAdminOrderStatus(adminToken, orderInfo.orderId(), OrderStatus.PREPARING).andExpect(status().isOk());
			Long itemId = onlyItemId(orderInfo.orderId());
			mockMvc.perform(post("/api/v1/orders/{orderId}/items/{itemId}/cancel", orderInfo.orderId(), itemId)
							.header(HttpHeaders.AUTHORIZATION, "Bearer " + accessToken)
							.contentType(MediaType.APPLICATION_JSON)
							.content(objectMapper.writeValueAsString(new OrderCancelRequest("변심"))))
					.andExpect(status().isOk());
			OrderClaim claim = onlyClaimFor(itemId);

			// when
			mockMvc.perform(post("/api/v1/order-claims/{claimId}/withdraw", claim.getId())
							.header(HttpHeaders.AUTHORIZATION, "Bearer " + accessToken))
					.andExpect(status().isOk())
					.andExpect(jsonPath("$.data.status", is("PREPARING")));

			// then
			assertThat(orderClaimRepository.findById(claim.getId()).orElseThrow().getStatus())
					.isEqualTo(OrderClaimStatus.WITHDRAWN);
			assertThat(orderItemRepository.findById(itemId).orElseThrow().getClaimStatus()).isNull();
		}

		@Test
		@DisplayName("환불 결과가 불명이면 클레임은 진행 중으로 남고, 대사가 마무리하면 그제서야 확정된다")
		void keepsClaimInProgressWhenRefundResultUnknownAndFinalizesLater() throws Exception {
			// given
			Member member = signup();
			String accessToken = login(member.getEmail());
			Address address = addressRepository.save(AddressFixture.create(member));
			Product product = seedProduct(5);
			OrderInfo orderInfo = createOrder(accessToken, product.getId(), 1, address.getId());
			String paymentKey = uniquePaymentKey();
			confirmAndGetPaymentId(accessToken, paymentKey, orderInfo.orderNumber(), orderInfo.finalAmount());
			Long itemId = onlyItemId(orderInfo.orderId());
			willThrow(new BusinessException(ErrorCode.PAYMENT_RESULT_UNKNOWN))
					.given(paymentClient).cancel(any(PaymentCancelCommand.class));

			// when: 즉시 취소를 시도하지만 토스 결과를 알 수 없다
			mockMvc.perform(post("/api/v1/orders/{orderId}/items/{itemId}/cancel", orderInfo.orderId(), itemId)
							.header(HttpHeaders.AUTHORIZATION, "Bearer " + accessToken)
							.contentType(MediaType.APPLICATION_JSON)
							.content(objectMapper.writeValueAsString(new OrderCancelRequest("변심"))))
					.andExpect(status().isOk())
					.andExpect(jsonPath("$.data.claimStatus", is("CANCEL_REQUEST")));

			// then: 상품주문은 아직 PAID, 클레임은 REQUESTED 로 남는다
			assertThat(orderItemRepository.findById(itemId).orElseThrow().getStatus())
					.isEqualTo(OrderItemStatus.PAID);
			OrderClaim claim = onlyClaimFor(itemId);
			assertThat(claim.getStatus()).isEqualTo(OrderClaimStatus.REQUESTED);

			// when: 대사가 나중에 결과를 확정한다
			orderClaimFinalizeService.applyRefundDone(claim.getId(), LocalDateTime.now(clock));

			// then
			OrderItem finalizedItem = orderItemRepository.findById(itemId).orElseThrow();
			assertThat(finalizedItem.getStatus()).isEqualTo(OrderItemStatus.CANCELED);
			assertThat(finalizedItem.getClaimStatus()).isEqualTo(OrderItemClaimStatus.CANCEL_DONE);
			assertThat(orderClaimRepository.findById(claim.getId()).orElseThrow().getStatus())
					.isEqualTo(OrderClaimStatus.DONE);
		}

		private String adminToken() {
			Member admin = memberRepository.save(
					Member.create("item-claim-admin-" + UUID.randomUUID() + "@groove.com", "encoded", "관리자"));
			return "Bearer " + jwtProvider.createAccessToken(admin.getId(), MemberRole.ADMIN);
		}

		private Long onlyItemId(Long orderId) {
			Order order = orderRepository.findWithItemsById(orderId).orElseThrow();
			return order.getItems().get(0).getId();
		}

		private OrderClaim onlyClaimFor(Long itemId) {
			List<OrderClaim> claims = orderClaimRepository.findAll().stream()
					.filter(claim -> claim.getOrderItem().getId().equals(itemId))
					.toList();
			assertThat(claims).hasSize(1);
			return claims.get(0);
		}

		private void setDeliveredAt(Long itemId, LocalDateTime deliveredAt) {
			jdbcTemplate.update("update order_item set delivered_at = ? where id = ?",
					Timestamp.valueOf(deliveredAt), itemId);
		}

		private void stubPartialCancelSuccess(String paymentKey) {
			given(paymentClient.cancel(any(PaymentCancelCommand.class))).willAnswer(invocation -> {
				PaymentCancelCommand command = invocation.getArgument(0);
				LocalDateTime canceledAt = LocalDateTime.now(clock).truncatedTo(ChronoUnit.SECONDS);
				return new PaymentCancelResult(paymentKey, "PARTIAL_CANCELED", canceledAt,
						"txn-" + command.idempotencyKey(), null);
			});
		}

		private TwoItemOrder createConfirmedTwoItemOrderWithCoupon(Member member, Product first, Product second,
				String paymentKey) {
			Order order = OrderFixture.create(member, "20260929-CLAIM" + UUID.randomUUID().toString()
					.replace("-", "").substring(0, 8).toUpperCase());
			order.addItem(first, 1);
			order.addItem(second, 1);
			orderRepository.saveAndFlush(order);
			String code = "CLAIM" + UUID.randomUUID().toString().replace("-", "").substring(0, 10).toUpperCase();
			Coupon coupon = couponRepository.save(Coupon.create(code, "클레임 테스트 쿠폰", DiscountType.FIXED,
					new BigDecimal("5000"), BigDecimal.ZERO, null, null, LocalDateTime.now(clock).plusDays(7)));
			MemberCoupon memberCoupon = memberCouponRepository.save(MemberCoupon.issue(member, coupon));
			order.applyCoupon(memberCoupon, memberCoupon.calculateDiscount(order.getTotalAmount()));
			memberCoupon.use(order.getId());
			memberCouponRepository.saveAndFlush(memberCoupon);
			order.markPaid();
			order.place(LocalDateTime.now(clock));
			orderRepository.saveAndFlush(order);
			Payment payment = PaymentFixture.approved(order, paymentKey);
			paymentRepository.saveAndFlush(payment);
			OrderItem firstItem = order.getItems().get(0);
			return new TwoItemOrder(order.getId(), firstItem, order.getItems().get(1).getId(), memberCoupon.getId());
		}

		private record TwoItemOrder(Long orderId, OrderItem firstItem, Long secondItemId, Long memberCouponId) {
		}
	}

	@Nested
	@DisplayName("한정반 결제 취소 통합 복구")
	class LimitedOrderCancel {

		@Test
		@DisplayName("한정반 결제를 취소하면 결제·주문·재고·선점이 한 흐름에서 모두 되돌아간다")
		void cancelsConfirmedLimitedOrderAndRestoresPaymentOrderStockAndReservationTogether() throws Exception {
			// given: 총 수량 1건짜리 드롭이라 이 구매 한 건으로 SOLD_OUT 이 된다
			int totalQuantity = 1;
			LimitedDropSetup setup = prepareOpenDropWithProduct(totalQuantity);
			Member member = signup();
			Address address = addressRepository.save(AddressFixture.create(member));
			LimitedPurchaseResponse purchase = limitedPurchaseService.purchase(setup.dropId(), member.getId(),
					address.getId());
			String accessToken = login(member.getEmail());
			String paymentKey = uniquePaymentKey();
			long paymentId = confirmAndGetPaymentId(accessToken, paymentKey, purchase.orderNumber(),
					purchase.finalAmount());
			String reason = "고객 변심";
			stubCancelSuccess(paymentKey);
			assertThat(limitedDropRepository.findById(setup.dropId()).orElseThrow().getStatus())
					.isEqualTo(LimitedDropStatus.SOLD_OUT);

			// when
			mockMvc.perform(post("/api/v1/payments/" + paymentId + "/cancel")
							.header(HttpHeaders.AUTHORIZATION, "Bearer " + accessToken)
							.contentType(MediaType.APPLICATION_JSON)
							.content(objectMapper.writeValueAsString(new PaymentCancelRequest(reason))))
					.andExpect(status().isOk())
					.andExpect(jsonPath("$.data.status", is("CANCELED")));

			// then: 결제·주문 상태가 되돌아간다
			Payment payment = paymentRepository.findById(paymentId).orElseThrow();
			assertThat(payment.getStatus()).isEqualTo(PaymentStatus.CANCELED);
			Order order = orderRepository.findById(purchase.orderId()).orElseThrow();
			assertThat(order.getStatus()).isEqualTo(OrderStatus.CANCELED);
			assertThat(order.getCancelReason()).isEqualTo(reason);

			// then: 재고와 이력이 되돌아간다
			Stock stock = stockRepository.findByProductId(setup.productId()).orElseThrow();
			assertThat(stock.getQuantity()).isEqualTo(totalQuantity);
			List<StockHistory> histories = stockHistoryRepository.findAllByStockIdOrderByCreatedAtAsc(stock.getId());
			assertThat(histories).hasSize(2);
			assertThat(histories.get(1).getChangeType()).isEqualTo(StockChangeType.CANCEL);

			// then: 한정반 구매 이력·판매 수량·상태가 되돌아간다
			assertThat(limitedPurchaseRepository.findByOrderId(purchase.orderId())).isEmpty();
			LimitedDrop reloadedDrop = limitedDropRepository.findById(setup.dropId()).orElseThrow();
			assertThat(reloadedDrop.getSoldCount()).isZero();
			assertThat(reloadedDrop.getStatus()).isEqualTo(LimitedDropStatus.OPEN);

			// then: Redis 선점(AFTER_COMMIT)이 되돌아간다. 요청이 끝난 지금 시점이면 커밋 후이다
			assertThat(redisTemplate.opsForValue().get(LimitedDropRedisService.stockKey(setup.dropId())))
					.isEqualTo(String.valueOf(totalQuantity));
			assertThat(redisTemplate.opsForSet().isMember(LimitedDropRedisService.buyersKey(setup.dropId()),
					String.valueOf(member.getId()))).isFalse();

			verify(paymentClient).cancel(eq(paymentKey), eq(reason), any());

			limitedDropRedisService.clear(setup.dropId());
		}
	}

	@Nested
	@DisplayName("PATCH /api/v1/admin/orders/{id}/status (관리자 취소)")
	class AdminCancel {

		@Test
		@DisplayName("관리자가 결제 완료 주문을 취소하면 결제도 취소되고 감사 로그 두 건이 IP 와 함께 남는다")
		void cancelsPaymentAndRecordsAuditLogsWhenAdminCancelsPaidOrder() throws Exception {
			// given
			Member member = signup();
			String accessToken = login(member.getEmail());
			Address address = addressRepository.save(AddressFixture.create(member));
			Product product = seedProduct(5);
			OrderInfo orderInfo = createOrder(accessToken, product.getId(), 1, address.getId());
			String paymentKey = uniquePaymentKey();
			long paymentId = confirmAndGetPaymentId(accessToken, paymentKey, orderInfo.orderNumber(),
					orderInfo.finalAmount());
			stubCancelSuccess(paymentKey);
			Member admin = memberRepository.save(
					Member.create("payment-admin-" + UUID.randomUUID() + "@groove.com", "encoded", "관리자"));
			String adminToken = "Bearer " + jwtProvider.createAccessToken(admin.getId(), MemberRole.ADMIN);

			// when
			mockMvc.perform(patch("/api/v1/admin/orders/{id}/status", orderInfo.orderId())
							.header(HttpHeaders.AUTHORIZATION, adminToken)
							.header("X-Forwarded-For", "203.0.113.7")
							.contentType(MediaType.APPLICATION_JSON)
							.content(objectMapper.writeValueAsString(
									new AdminOrderStatusChangeRequest(OrderStatus.CANCELED))))
					.andExpect(status().isOk())
					.andExpect(jsonPath("$.data.status", is("CANCELED")));

			// then
			assertThat(paymentRepository.findById(paymentId).orElseThrow().getStatus())
					.isEqualTo(PaymentStatus.CANCELED);
			verify(paymentClient).cancel(eq(paymentKey), any(), any());
			List<AdminAuditLog> logs = adminAuditLogRepository.findAllByAdminIdOrderByIdAsc(admin.getId());
			assertThat(logs).extracting(AdminAuditLog::getAction)
					.containsExactly(AdminAuditAction.ORDER_STATUS_CHANGE, AdminAuditAction.PAYMENT_CANCEL);
			assertThat(logs).extracting(AdminAuditLog::getIpAddress).containsOnly("203.0.113.7");
			assertThat(logs.get(1).getTargetType()).isEqualTo(AdminAuditTargetType.PAYMENT);
			assertThat(logs.get(1).getTargetId()).isEqualTo(paymentId);
		}

		@Test
		@DisplayName("토스 취소 결과를 알 수 없으면 CANCEL_REQUESTED 감사 로그 두 건을 남긴다")
		void recordsInProgressAuditLogsWhenTossResultIsUnknown() throws Exception {
			// given
			Member member = signup();
			String accessToken = login(member.getEmail());
			Address address = addressRepository.save(AddressFixture.create(member));
			Product product = seedProduct(5);
			OrderInfo orderInfo = createOrder(accessToken, product.getId(), 1, address.getId());
			String paymentKey = uniquePaymentKey();
			long paymentId = confirmAndGetPaymentId(accessToken, paymentKey, orderInfo.orderNumber(),
					orderInfo.finalAmount());
			willThrow(new BusinessException(ErrorCode.PAYMENT_RESULT_UNKNOWN))
					.given(paymentClient).cancel(eq(paymentKey), any(), any());
			Member admin = memberRepository.save(
					Member.create("payment-admin-" + UUID.randomUUID() + "@groove.com", "encoded", "관리자"));
			String adminToken = "Bearer " + jwtProvider.createAccessToken(admin.getId(), MemberRole.ADMIN);

			// when
			mockMvc.perform(patch("/api/v1/admin/orders/{id}/status", orderInfo.orderId())
						.header(HttpHeaders.AUTHORIZATION, adminToken)
						.contentType(MediaType.APPLICATION_JSON)
						.content(objectMapper.writeValueAsString(
								new AdminOrderStatusChangeRequest(OrderStatus.CANCELED))))
					.andExpect(status().isOk())
					.andExpect(jsonPath("$.data.paymentStatus", is("CANCEL_REQUESTED")));

			// then
			List<AdminAuditLog> logs = adminAuditLogRepository.findAllByAdminIdOrderByIdAsc(admin.getId());
			assertThat(logs).extracting(AdminAuditLog::getDetail)
					.containsExactly("PAID->CANCEL_REQUESTED", "DONE->CANCEL_REQUESTED");
			assertThat(logs.get(1).getTargetId()).isEqualTo(paymentId);
		}

		@Test
		@DisplayName("CANCEL_REQUESTED 중이면 PAID 와 PREPARING 주문의 다음 배송 상태 전이를 거절한다")
		void rejectsShippingTransitionsWhileCancelRequested() throws Exception {
			// given: PAID 주문
			Member member = signup();
			String accessToken = login(member.getEmail());
			Address address = addressRepository.save(AddressFixture.create(member));
			Product firstProduct = seedProduct(5);
			OrderInfo paidOrder = createOrder(accessToken, firstProduct.getId(), 1, address.getId());
			String firstPaymentKey = uniquePaymentKey();
			long firstPaymentId = confirmAndGetPaymentId(accessToken, firstPaymentKey, paidOrder.orderNumber(),
					paidOrder.finalAmount());
			Member admin = memberRepository.save(
					Member.create("payment-admin-" + UUID.randomUUID() + "@groove.com", "encoded", "관리자"));
			String adminToken = "Bearer " + jwtProvider.createAccessToken(admin.getId(), MemberRole.ADMIN);
			willThrow(new BusinessException(ErrorCode.PAYMENT_RESULT_UNKNOWN))
					.given(paymentClient).cancel(eq(firstPaymentKey), any(), any());
			requestPaymentCancel(accessToken, firstPaymentId);

			// when & then: PAID -> PREPARING
			changeAdminOrderStatus(adminToken, paidOrder.orderId(), OrderStatus.PREPARING)
					.andExpect(status().isConflict())
					.andExpect(jsonPath("$.error.code", is("ORDER_CANCEL_IN_PROGRESS")));

			// given: PREPARING 주문
			reset(paymentClient);
			Product secondProduct = seedProduct(5);
			OrderInfo preparingOrder = createOrder(accessToken, secondProduct.getId(), 1, address.getId());
			String secondPaymentKey = uniquePaymentKey();
			confirmAndGetPaymentId(accessToken, secondPaymentKey, preparingOrder.orderNumber(),
					preparingOrder.finalAmount());
			changeAdminOrderStatus(adminToken, preparingOrder.orderId(), OrderStatus.PREPARING)
					.andExpect(status().isOk());
			willThrow(new BusinessException(ErrorCode.PAYMENT_RESULT_UNKNOWN))
					.given(paymentClient).cancel(eq(secondPaymentKey), any(), any());
			changeAdminOrderStatus(adminToken, preparingOrder.orderId(), OrderStatus.CANCELED)
					.andExpect(status().isOk());

			// when & then: PREPARING -> SHIPPED
			changeAdminOrderStatus(adminToken, preparingOrder.orderId(), OrderStatus.SHIPPED)
					.andExpect(status().isConflict())
					.andExpect(jsonPath("$.error.code", is("ORDER_CANCEL_IN_PROGRESS")));
		}
	}

	private record OrderInfo(Long orderId, String orderNumber, BigDecimal finalAmount) {
	}

	private record LimitedDropSetup(Long dropId, Long productId) {
	}

	private record CouponOrderInfo(Long orderId, String orderNumber, BigDecimal finalAmount, Long memberCouponId) {
	}

	private String uniquePaymentKey() {
		return "tviva-" + UUID.randomUUID();
	}

	private long confirmAndGetPaymentId(String accessToken, String paymentKey, String orderNumber, BigDecimal amount)
			throws Exception {
		stubConfirmSuccess(paymentKey, orderNumber, amount);
		MvcResult result = mockMvc.perform(post("/api/v1/payments/confirm")
						.header(HttpHeaders.AUTHORIZATION, "Bearer " + accessToken)
						.contentType(MediaType.APPLICATION_JSON)
						.content(objectMapper.writeValueAsString(
								confirmRequest(paymentKey, orderNumber, amount.longValueExact()))))
				.andExpect(status().isOk())
				.andReturn();
		return objectMapper.readTree(result.getResponse().getContentAsString()).path("data").path("paymentId")
				.asLong();
	}

	private CouponOrderInfo createOrderWithCoupon(String accessToken, Long productId, int quantity, Long addressId)
			throws Exception {
		String code = "CANCEL" + UUID.randomUUID().toString().replace("-", "").substring(0, 10).toUpperCase();
		couponRepository.save(Coupon.create(code, "결제 취소 테스트 쿠폰", DiscountType.FIXED, new BigDecimal("5000"),
				BigDecimal.ZERO, null, null, LocalDateTime.now(clock).plusDays(7)));
		MvcResult issueResult = mockMvc.perform(post("/api/v1/coupons/issue")
						.header(HttpHeaders.AUTHORIZATION, "Bearer " + accessToken)
						.contentType(MediaType.APPLICATION_JSON)
						.content(objectMapper.writeValueAsString(new CouponIssueRequest(code))))
				.andExpect(status().isCreated())
				.andReturn();
		long memberCouponId = objectMapper.readTree(issueResult.getResponse().getContentAsString())
				.path("data").path("memberCouponId").asLong();

		OrderCreateRequest createRequest = OrderFixture.directRequestWithCoupon(productId, quantity, addressId,
				memberCouponId);
		MvcResult createResult = mockMvc.perform(post("/api/v1/orders")
						.header(HttpHeaders.AUTHORIZATION, "Bearer " + accessToken)
						.contentType(MediaType.APPLICATION_JSON)
						.content(objectMapper.writeValueAsString(createRequest)))
				.andExpect(status().isCreated())
				.andReturn();
		JsonNode data = objectMapper.readTree(createResult.getResponse().getContentAsString()).path("data");
		return new CouponOrderInfo(data.path("orderId").asLong(), data.path("orderNumber").asText(),
				new BigDecimal(data.path("finalAmount").asText()), memberCouponId);
	}

	private void stubConfirmSuccess(String paymentKey, String orderNumber, BigDecimal amount) {
		LocalDateTime approvedAt = LocalDateTime.now(clock).truncatedTo(ChronoUnit.SECONDS);
		given(paymentClient.confirm(eq(paymentKey), eq(orderNumber), any(BigDecimal.class)))
				.willReturn(new PaymentConfirmResult(paymentKey, orderNumber, "카드", amount, approvedAt));
	}

	private void stubCancelSuccess(String paymentKey) {
		LocalDateTime canceledAt = LocalDateTime.now(clock).truncatedTo(ChronoUnit.SECONDS);
		given(paymentClient.cancel(eq(paymentKey), any(), any()))
				.willReturn(PaymentCancelResult.of(paymentKey, "CANCELED", canceledAt));
	}

	private ResultActions confirm(String accessToken, String paymentKey, OrderInfo orderInfo) throws Exception {
		long amount = orderInfo.finalAmount().longValueExact();
		return mockMvc.perform(post("/api/v1/payments/confirm")
				.header(HttpHeaders.AUTHORIZATION, "Bearer " + accessToken)
				.contentType(MediaType.APPLICATION_JSON)
				.content(objectMapper.writeValueAsString(
						confirmRequest(paymentKey, orderInfo.orderNumber(), amount))));
	}

	private void requestPaymentCancel(String accessToken, long paymentId) throws Exception {
		mockMvc.perform(post("/api/v1/payments/" + paymentId + "/cancel")
					.header(HttpHeaders.AUTHORIZATION, "Bearer " + accessToken)
					.contentType(MediaType.APPLICATION_JSON)
					.content(objectMapper.writeValueAsString(new PaymentCancelRequest("고객 변심"))))
				.andExpect(status().isOk());
	}

	private ResultActions changeAdminOrderStatus(String adminToken, Long orderId, OrderStatus status)
			throws Exception {
		return mockMvc.perform(patch("/api/v1/admin/orders/{id}/status", orderId)
				.header(HttpHeaders.AUTHORIZATION, adminToken)
				.contentType(MediaType.APPLICATION_JSON)
				.content(objectMapper.writeValueAsString(new AdminOrderStatusChangeRequest(status))));
	}

	private PaymentConfirmRequest confirmRequest(String paymentKey, String orderNumber, long amount) {
		return new PaymentConfirmRequest(paymentKey, orderNumber, amount);
	}

	private OrderInfo createOrder(String accessToken, Long productId, int quantity, Long addressId)
			throws Exception {
		OrderCreateRequest createRequest = new OrderCreateRequest(null, productId, quantity, addressId, null);
		MvcResult result = mockMvc.perform(post("/api/v1/orders")
						.header(HttpHeaders.AUTHORIZATION, "Bearer " + accessToken)
						.contentType(MediaType.APPLICATION_JSON)
						.content(objectMapper.writeValueAsString(createRequest)))
				.andExpect(status().isCreated())
				.andReturn();
		JsonNode data = objectMapper.readTree(result.getResponse().getContentAsString()).path("data");
		return new OrderInfo(data.path("orderId").asLong(), data.path("orderNumber").asText(),
				new BigDecimal(data.path("finalAmount").asText()));
	}

	private Product seedProduct(int stockQuantity) {
		Artist artist = artistRepository.save(ArtistFixture.create());
		Product createdProduct = ProductFixture.create(artist);
		albumRepository.save(createdProduct.getAlbum());
		Product product = productRepository.save(createdProduct);
		stockRepository.save(StockFixture.create(product, stockQuantity));
		return product;
	}

	private Long prepareOpenDrop(int totalQuantity) {
		Artist artist = artistRepository.save(ArtistFixture.create());
		Product createdProduct = ProductFixture.create(artist);
		albumRepository.save(createdProduct.getAlbum());
		Product product = productRepository.save(createdProduct);
		stockRepository.saveAndFlush(StockFixture.create(product, totalQuantity));

		LimitedDrop drop = LimitedDropFixture.scheduled(product, totalQuantity, Math.min(2, totalQuantity));
		drop.open();
		// 서비스는 Asia/Seoul Clock 을 쓰므로 시스템 시각으로 잡으면 UTC 러너에서 드롭이 마감된 것으로 판정된다.
		LocalDateTime now = LocalDateTime.now(clock);
		LimitedDropFixture.withOpenAt(drop, now.minusHours(1));
		LimitedDropFixture.withCloseAt(drop, now.plusHours(1));
		limitedDropRepository.saveAndFlush(drop);

		Long dropId = drop.getId();
		limitedDropRedisService.clear(dropId);
		limitedDropRedisService.initStock(dropId, totalQuantity);
		return dropId;
	}

	/** prepareOpenDrop 과 같은 세팅이지만, 취소 후 재고 복구를 상품 단위로 단언해야 해서 productId 도 함께 돌려준다. */
	private LimitedDropSetup prepareOpenDropWithProduct(int totalQuantity) {
		Artist artist = artistRepository.save(ArtistFixture.create());
		Product createdProduct = ProductFixture.create(artist);
		albumRepository.save(createdProduct.getAlbum());
		Product product = productRepository.save(createdProduct);
		stockRepository.saveAndFlush(StockFixture.create(product, totalQuantity));

		LimitedDrop drop = LimitedDropFixture.scheduled(product, totalQuantity, Math.min(2, totalQuantity));
		drop.open();
		// 서비스는 Asia/Seoul Clock 을 쓰므로 시스템 시각으로 잡으면 UTC 러너에서 드롭이 마감된 것으로 판정된다.
		LocalDateTime now = LocalDateTime.now(clock);
		LimitedDropFixture.withOpenAt(drop, now.minusHours(1));
		LimitedDropFixture.withCloseAt(drop, now.plusHours(1));
		limitedDropRepository.saveAndFlush(drop);

		Long dropId = drop.getId();
		limitedDropRedisService.clear(dropId);
		limitedDropRedisService.initStock(dropId, totalQuantity);
		return new LimitedDropSetup(dropId, product.getId());
	}

	private Member signup() throws Exception {
		String email = "payment-flow-" + UUID.randomUUID() + "@groove.com";
		mockMvc.perform(post("/api/v1/auth/signup")
						.contentType(MediaType.APPLICATION_JSON)
						.content(objectMapper.writeValueAsString(new SignupRequest(email, "password1", "그루버"))))
				.andExpect(status().isCreated());
		return memberRepository.findByEmail(email).orElseThrow();
	}

	private String login(String email) throws Exception {
		MvcResult loginResult = mockMvc.perform(post("/api/v1/auth/login")
						.contentType(MediaType.APPLICATION_JSON)
						.content(objectMapper.writeValueAsString(new LoginRequest(email, "password1"))))
				.andExpect(status().isOk())
				.andReturn();
		return objectMapper.readTree(loginResult.getResponse().getContentAsString())
				.path("data").path("accessToken").asText();
	}
}
