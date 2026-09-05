package com.groove.payment;

import static org.assertj.core.api.Assertions.assertThat;
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
import java.time.Clock;
import java.time.LocalDateTime;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.UUID;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.ResultActions;

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
import com.groove.limited.repository.LimitedDropRepository;
import com.groove.limited.repository.LimitedPurchaseRepository;
import com.groove.limited.service.LimitedDropRedisService;
import com.groove.limited.service.LimitedPurchaseService;
import com.groove.member.entity.Address;
import com.groove.member.entity.Member;
import com.groove.member.entity.MemberRole;
import com.groove.member.repository.AddressRepository;
import com.groove.member.repository.MemberRepository;
import com.groove.order.dto.AdminOrderStatusChangeRequest;
import com.groove.order.dto.OrderCreateRequest;
import com.groove.order.entity.Order;
import com.groove.order.entity.OrderStatus;
import com.groove.order.repository.OrderRepository;
import com.groove.order.scheduler.OrderExpirationScheduler;
import com.groove.payment.client.PaymentClient;
import com.groove.payment.client.dto.PaymentCancelResult;
import com.groove.payment.client.dto.PaymentConfirmResult;
import com.groove.payment.dto.PaymentCancelRequest;
import com.groove.payment.dto.PaymentConfirmRequest;
import com.groove.payment.entity.Payment;
import com.groove.payment.entity.PaymentStatus;
import com.groove.payment.repository.PaymentRepository;
import com.groove.product.entity.Artist;
import com.groove.product.entity.Product;
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
			verify(paymentClient).cancel(eq(paymentKey), eq(reason));
		}

		@Test
		@DisplayName("한정반 주문을 취소하면 선점이 되돌아가 Redis 재고와 구매자 목록이 복구된다")
		void cancelsPaidLimitedOrderAndRestoresRedisReservation() throws Exception {
			// given
			int totalQuantity = 5;
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
					.given(paymentClient).cancel(eq(paymentKey), any());

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
			Stock stock = stockRepository.findByProductId(product.getId()).orElseThrow();
			assertThat(stock.getQuantity()).isEqualTo(4);
			assertThat(stockHistoryRepository.findAllByStockIdOrderByCreatedAtAsc(stock.getId()))
					.extracting(StockHistory::getChangeType)
					.doesNotContain(StockChangeType.CANCEL);
			MemberCoupon memberCoupon = memberCouponRepository.findById(orderInfo.memberCouponId()).orElseThrow();
			assertThat(memberCoupon.isUsed()).isTrue();
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
			given(paymentClient.cancel(eq(paymentKey), any()))
					.willReturn(new PaymentCancelResult(paymentKey, "CANCELED",
							LocalDateTime.now(clock).truncatedTo(ChronoUnit.SECONDS)));

			// when
			mockMvc.perform(post("/api/v1/orders/" + orderInfo.orderId() + "/cancel")
							.header(HttpHeaders.AUTHORIZATION, "Bearer " + accessToken))
					.andExpect(status().isOk())
					.andExpect(jsonPath("$.data.status", is("CANCELED")));

			// then
			Payment payment = paymentRepository.findById(paymentId).orElseThrow();
			assertThat(payment.getStatus()).isEqualTo(PaymentStatus.CANCELED);
			verify(paymentClient).cancel(eq(paymentKey), any());
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
			verify(paymentClient, never()).cancel(any(), any());
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
			verify(paymentClient).cancel(eq(paymentKey), any());
			List<AdminAuditLog> logs = adminAuditLogRepository.findAllByAdminIdOrderByIdAsc(admin.getId());
			assertThat(logs).extracting(AdminAuditLog::getAction)
					.containsExactly(AdminAuditAction.ORDER_STATUS_CHANGE, AdminAuditAction.PAYMENT_CANCEL);
			assertThat(logs).extracting(AdminAuditLog::getIpAddress).containsOnly("203.0.113.7");
			assertThat(logs.get(1).getTargetType()).isEqualTo(AdminAuditTargetType.PAYMENT);
			assertThat(logs.get(1).getTargetId()).isEqualTo(paymentId);
		}
	}

	private record OrderInfo(Long orderId, String orderNumber, BigDecimal finalAmount) {
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
		given(paymentClient.cancel(eq(paymentKey), any()))
				.willReturn(new PaymentCancelResult(paymentKey, "CANCELED", canceledAt));
	}

	private ResultActions confirm(String accessToken, String paymentKey, OrderInfo orderInfo) throws Exception {
		long amount = orderInfo.finalAmount().longValueExact();
		return mockMvc.perform(post("/api/v1/payments/confirm")
				.header(HttpHeaders.AUTHORIZATION, "Bearer " + accessToken)
				.contentType(MediaType.APPLICATION_JSON)
				.content(objectMapper.writeValueAsString(
						confirmRequest(paymentKey, orderInfo.orderNumber(), amount))));
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
		Product product = productRepository.save(ProductFixture.create(artist));
		stockRepository.save(StockFixture.create(product, stockQuantity));
		return product;
	}

	private Long prepareOpenDrop(int totalQuantity) {
		Artist artist = artistRepository.save(ArtistFixture.create());
		Product product = productRepository.save(ProductFixture.create(artist));
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
