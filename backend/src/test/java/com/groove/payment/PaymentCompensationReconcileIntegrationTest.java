package com.groove.payment;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.willThrow;

import java.math.BigDecimal;
import java.sql.Timestamp;
import java.time.Clock;
import java.time.LocalDateTime;
import java.time.temporal.ChronoUnit;
import java.util.UUID;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.bean.override.mockito.MockitoBean;

import com.groove.fixture.AddressFixture;
import com.groove.fixture.ArtistFixture;
import com.groove.fixture.MemberFixture;
import com.groove.fixture.OrderFixture;
import com.groove.fixture.ProductFixture;
import com.groove.fixture.StockFixture;
import com.groove.global.common.BusinessException;
import com.groove.global.common.ErrorCode;
import com.groove.inventory.repository.StockRepository;
import com.groove.member.entity.Address;
import com.groove.member.entity.Member;
import com.groove.member.repository.AddressRepository;
import com.groove.member.repository.MemberRepository;
import com.groove.order.dto.OrderCreateResponse;
import com.groove.order.entity.Order;
import com.groove.order.repository.OrderRepository;
import com.groove.order.service.OrderService;
import com.groove.payment.client.PaymentClient;
import com.groove.payment.client.dto.PaymentCancelResult;
import com.groove.payment.entity.PaymentCompensation;
import com.groove.payment.entity.PaymentCompensationStatus;
import com.groove.payment.repository.PaymentCompensationRepository;
import com.groove.payment.scheduler.PaymentReconcileScheduler;
import com.groove.payment.service.CompensationResult;
import com.groove.payment.service.PaymentCompensator;
import com.groove.product.entity.Artist;
import com.groove.product.entity.Product;
import com.groove.product.repository.AlbumRepository;
import com.groove.product.repository.ArtistRepository;
import com.groove.product.repository.ProductRepository;
import com.groove.support.IntegrationTestSupport;

/**
 * payment 행이 없는 보상 취소 대기 큐(payment_compensation)가 대사 스케줄러로 회수되는지 검증한다. 대사 후보
 * 조회는 전역이라 자기 행의 updated_at 을 과거로 밀어 배치 앞쪽에 세우고, 단언은 자기 paymentKey 로만 한다.
 */
class PaymentCompensationReconcileIntegrationTest extends IntegrationTestSupport {

	@Autowired
	private MemberRepository memberRepository;

	@Autowired
	private AddressRepository addressRepository;

	@Autowired
	private ArtistRepository artistRepository;

	@Autowired
	private AlbumRepository albumRepository;

	@Autowired
	private ProductRepository productRepository;

	@Autowired
	private StockRepository stockRepository;

	@Autowired
	private OrderRepository orderRepository;

	@Autowired
	private OrderService orderService;

	@Autowired
	private PaymentReconcileScheduler paymentReconcileScheduler;

	@Autowired
	private PaymentCompensator paymentCompensator;

	@Autowired
	private PaymentCompensationRepository compensationRepository;

	@Autowired
	private JdbcTemplate jdbcTemplate;

	@Autowired
	private Clock clock;

	@MockitoBean
	private PaymentClient paymentClient;

	@Nested
	@DisplayName("보상 대기 회수")
	class Reconcile {

		@Test
		@DisplayName("동기 보상 취소가 실패해 대기 행이 PENDING 으로 남아도, grace 뒤 재시도가 성공하면 CANCELED 로 수렴한다")
		void resolvesToCanceledAfterRetrySucceedsFollowingInitialFailure() {
			// given
			SeededOrder seeded = seedPendingOrder(5, 1);
			String paymentKey = "dup-" + UUID.randomUUID();
			LocalDateTime approvedAt = now().minusMinutes(10).truncatedTo(ChronoUnit.SECONDS);
			BusinessException resultUnknown = new BusinessException(ErrorCode.PAYMENT_RESULT_UNKNOWN,
					"TOSS 통신 실패: Read timed out");
			willThrow(resultUnknown).given(paymentClient)
					.cancel(eq(paymentKey), eq(PaymentCompensator.DUPLICATE_APPROVAL_REASON));

			// when: 동기 보상 취소가 실패해 대기 행이 PENDING 으로 커밋된다.
			CompensationResult firstAttempt = paymentCompensator.cancelApproved(null, paymentKey, approvedAt,
					PaymentCompensator.DUPLICATE_APPROVAL_REASON, seeded.orderId(), seeded.orderNumber());

			// then
			assertThat(firstAttempt.canceled()).isFalse();
			PaymentCompensation pending = compensationRepository.findByPaymentKey(paymentKey).orElseThrow();
			assertThat(pending.getStatus()).isEqualTo(PaymentCompensationStatus.PENDING);
			assertThat(pending.getAttempts()).isEqualTo(1);

			// given: grace 를 지나게 하고, 재시도는 성공하도록 스텁을 바꾼다.
			jdbcTemplate.update("update payment_compensation set updated_at = ? where id = ?",
					Timestamp.valueOf(oldUpdatedAt()), pending.getId());
			LocalDateTime canceledAt = now().minusMinutes(1).truncatedTo(ChronoUnit.SECONDS);
			given(paymentClient.cancel(eq(paymentKey), eq(PaymentCompensator.DUPLICATE_APPROVAL_REASON)))
					.willReturn(new PaymentCancelResult(paymentKey, "CANCELED", canceledAt));

			// when
			paymentReconcileScheduler.reconcile();

			// then
			PaymentCompensation resolved = compensationRepository.findByPaymentKey(paymentKey).orElseThrow();
			assertThat(resolved.getStatus()).isEqualTo(PaymentCompensationStatus.CANCELED);
			assertThat(resolved.getCanceledAt()).isEqualTo(canceledAt);
		}

		@Test
		@DisplayName("이미 취소된 결제로 응답(취소 성공 흡수)되면 CANCELED 로 수렴한다")
		void resolvesToCanceledWhenAlreadyCanceledIsAbsorbedAsSuccess() {
			// given: PaymentClient 구현체(TossPaymentClient)가 ALREADY_CANCELED_PAYMENT 를 이미
			// 성공으로 흡수해 돌려주므로, 이 경계에서는 그 성공 응답만 스텁하면 된다.
			SeededOrder seeded = seedPendingOrder(5, 1);
			String paymentKey = "dup-" + UUID.randomUUID();
			PaymentCompensation seededCompensation = seedPendingCompensation(seeded.orderId(), seeded.orderNumber(),
					paymentKey);
			LocalDateTime canceledAt = now().minusMinutes(1).truncatedTo(ChronoUnit.SECONDS);
			given(paymentClient.cancel(eq(paymentKey), eq(PaymentCompensator.DUPLICATE_APPROVAL_REASON)))
					.willReturn(new PaymentCancelResult(paymentKey, "CANCELED", canceledAt));

			// when
			paymentReconcileScheduler.reconcile();

			// then
			PaymentCompensation resolved = compensationRepository.findById(seededCompensation.getId()).orElseThrow();
			assertThat(resolved.getStatus()).isEqualTo(PaymentCompensationStatus.CANCELED);
			assertThat(resolved.getCanceledAt()).isEqualTo(canceledAt);
		}

		@Test
		@DisplayName("재시도 상한에 닿으면 MANUAL_REVIEW 로 확정하고 재시도를 멈춘다")
		void marksManualReviewWhenMaxAttemptsReached() {
			// given
			SeededOrder seeded = seedPendingOrder(5, 1);
			String paymentKey = "dup-" + UUID.randomUUID();
			PaymentCompensation seededCompensation = seedPendingCompensation(seeded.orderId(), seeded.orderNumber(),
					paymentKey);
			jdbcTemplate.update("update payment_compensation set attempts = 9 where id = ?",
					seededCompensation.getId());
			BusinessException resultUnknown = new BusinessException(ErrorCode.PAYMENT_RESULT_UNKNOWN,
					"TOSS 통신 실패: Read timed out");
			willThrow(resultUnknown).given(paymentClient)
					.cancel(eq(paymentKey), eq(PaymentCompensator.DUPLICATE_APPROVAL_REASON));

			// when
			paymentReconcileScheduler.reconcile();

			// then
			PaymentCompensation resolved = compensationRepository.findById(seededCompensation.getId()).orElseThrow();
			assertThat(resolved.getStatus()).isEqualTo(PaymentCompensationStatus.MANUAL_REVIEW);
			assertThat(resolved.getAttempts()).isEqualTo(10);
		}

		@Test
		@DisplayName("토스가 명확히 거절하면 상한을 기다리지 않고 즉시 MANUAL_REVIEW 로 넘긴다")
		void marksManualReviewImmediatelyWhenTossRejects() {
			// given
			SeededOrder seeded = seedPendingOrder(5, 1);
			String paymentKey = "dup-" + UUID.randomUUID();
			PaymentCompensation seededCompensation = seedPendingCompensation(seeded.orderId(), seeded.orderNumber(),
					paymentKey);
			BusinessException rejection = new BusinessException(ErrorCode.PAYMENT_CANCEL_FAILED, "TOSS 거절");
			willThrow(rejection).given(paymentClient)
					.cancel(eq(paymentKey), eq(PaymentCompensator.DUPLICATE_APPROVAL_REASON));

			// when
			paymentReconcileScheduler.reconcile();

			// then
			PaymentCompensation resolved = compensationRepository.findById(seededCompensation.getId()).orElseThrow();
			assertThat(resolved.getStatus()).isEqualTo(PaymentCompensationStatus.MANUAL_REVIEW);
			assertThat(resolved.getAttempts()).isEqualTo(1);
		}
	}

	private LocalDateTime now() {
		return LocalDateTime.now(clock);
	}

	private LocalDateTime oldUpdatedAt() {
		return now().minusDays(30).truncatedTo(ChronoUnit.SECONDS);
	}

	/** enqueue() 를 직접 부르는 대신 대기 행을 seed 하고, updated_at 을 grace 밖으로 밀어 후보로 만든다. */
	private PaymentCompensation seedPendingCompensation(Long orderId, String orderNumber, String paymentKey) {
		Order order = orderRepository.findById(orderId).orElseThrow();
		PaymentCompensation compensation = PaymentCompensation.pending(paymentKey, order, orderNumber,
				now().minusMinutes(10).truncatedTo(ChronoUnit.SECONDS), PaymentCompensator.DUPLICATE_APPROVAL_REASON);
		PaymentCompensation saved = compensationRepository.saveAndFlush(compensation);
		jdbcTemplate.update("update payment_compensation set updated_at = ? where id = ?",
				Timestamp.valueOf(oldUpdatedAt()), saved.getId());
		return compensationRepository.findById(saved.getId()).orElseThrow();
	}

	private SeededOrder seedPendingOrder(int stockQuantity, int purchaseQuantity) {
		Member member = memberRepository.save(MemberFixture.create("payment-comp-" + UUID.randomUUID()
				+ "@groove.com"));
		Address address = addressRepository.save(AddressFixture.create(member));
		Artist artist = artistRepository.save(ArtistFixture.create());
		Product createdProduct = ProductFixture.create(artist);
		albumRepository.save(createdProduct.getAlbum());
		Product product = productRepository.save(createdProduct);
		stockRepository.saveAndFlush(StockFixture.create(product, stockQuantity));
		OrderCreateResponse response = orderService.create(member.getId(),
				OrderFixture.directRequest(product.getId(), purchaseQuantity, address.getId()));
		return new SeededOrder(member.getId(), product, response.orderId(), response.orderNumber(),
				response.finalAmount());
	}

	private record SeededOrder(Long memberId, Product product, Long orderId, String orderNumber,
			BigDecimal finalAmount) {
	}
}
