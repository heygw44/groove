package com.groove.payment;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.Clock;
import java.time.LocalDateTime;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.orm.ObjectOptimisticLockingFailureException;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

import com.groove.fixture.ArtistFixture;
import com.groove.fixture.MemberFixture;
import com.groove.fixture.OrderFixture;
import com.groove.fixture.ProductFixture;
import com.groove.fixture.StockFixture;
import com.groove.inventory.repository.StockRepository;
import com.groove.member.entity.Member;
import com.groove.member.repository.MemberRepository;
import com.groove.order.entity.Order;
import com.groove.order.entity.OrderStatus;
import com.groove.order.repository.OrderRepository;
import com.groove.payment.client.PaymentClient;
import com.groove.payment.client.dto.PaymentConfirmResult;
import com.groove.payment.entity.Payment;
import com.groove.payment.entity.PaymentStatus;
import com.groove.payment.repository.PaymentRepository;
import com.groove.payment.service.PaymentConfirmWriter;
import com.groove.product.entity.Artist;
import com.groove.product.entity.Product;
import com.groove.product.repository.AlbumRepository;
import com.groove.product.repository.ArtistRepository;
import com.groove.product.repository.ProductRepository;
import com.groove.support.IntegrationTestSupport;

/**
 * approve() 커밋과 fail() 이 겹쳐도 최종 결제는 DONE 으로 남는지 검증한다. 새 컨텍스트 조합을 만들지
 * 않으려고 {@link com.groove.limited.LimitedPurchaseConcurrencyIntegrationTest} 와 같은 목 구성을 쓴다.
 */
class PaymentConfirmWriterIntegrationTest extends IntegrationTestSupport {

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
	private OrderRepository orderRepository;

	@Autowired
	private PaymentRepository paymentRepository;

	@Autowired
	private PaymentConfirmWriter writer;

	@Autowired
	private PlatformTransactionManager transactionManager;

	@Autowired
	private Clock clock;

	@MockitoBean
	private PaymentClient paymentClient;

	private Order seedPendingOrder() {
		Member member = memberRepository.save(MemberFixture.create("writer-cc-" + UUID.randomUUID() + "@groove.com"));
		Artist artist = artistRepository.save(ArtistFixture.create());
		Product createdProduct = ProductFixture.create(artist);
		albumRepository.save(createdProduct.getAlbum());
		Product product = productRepository.save(createdProduct);
		stockRepository.saveAndFlush(StockFixture.create(product, 5));
		return orderRepository.saveAndFlush(OrderFixture.createWithItems(member, List.of(product)));
	}

	@Nested
	@DisplayName("fail()")
	class Fail {

		@Test
		@DisplayName("승인 커밋과 겹치면 버전 충돌로 지고 최종 결제는 DONE 으로 남는다")
		void keepsDoneWhenApproveCommitsWhileFailIsInterleaved() throws Exception {
			// given
			Order order = seedPendingOrder();
			Payment payment = paymentRepository.saveAndFlush(Payment.ready(order));
			Long paymentId = payment.getId();
			String paymentKey = "tviva-" + UUID.randomUUID();
			LocalDateTime approvedAt = LocalDateTime.now(clock).truncatedTo(ChronoUnit.SECONDS);
			PaymentConfirmResult result = new PaymentConfirmResult(paymentKey, order.getOrderNumber(), "카드",
					order.getFinalAmount(), approvedAt);
			TransactionTemplate transactionTemplate = new TransactionTemplate(transactionManager);
			ExecutorService executorService = Executors.newSingleThreadExecutor();

			// when: 바깥 트랜잭션이 fail() 로 READY 를 메모리에서 FAILED 로 바꾼 채(아직 미flush) 대기하는
			// 동안, 별도 스레드의 approve() 가 먼저 커밋을 끝내 버전을 올린다. 바깥 트랜잭션은 커밋 시점에야
			// version = 0 UPDATE 가 0행을 맞아 충돌한다.
			try {
				assertThatThrownBy(() -> transactionTemplate.executeWithoutResult(status -> {
					writer.fail(paymentId, "timeout");
					Future<?> approveFuture = executorService.submit(
							() -> writer.approve(paymentId, paymentKey, result));
					try {
						approveFuture.get(30, TimeUnit.SECONDS);
					} catch (Exception ex) {
						throw new IllegalStateException(ex);
					}
				})).isInstanceOf(ObjectOptimisticLockingFailureException.class);
			} finally {
				executorService.shutdown();
			}

			// then
			Payment reloadedPayment = paymentRepository.findById(paymentId).orElseThrow();
			assertThat(reloadedPayment.getStatus()).isEqualTo(PaymentStatus.DONE);
			assertThat(reloadedPayment.getPaymentKey()).isEqualTo(paymentKey);
			Order reloadedOrder = orderRepository.findById(order.getId()).orElseThrow();
			assertThat(reloadedOrder.getStatus()).isEqualTo(OrderStatus.PAID);
		}

		@Test
		@DisplayName("이미 승인이 커밋된 뒤에 단독으로 호출하면 아무 것도 바꾸지 않는다")
		void doesNothingWhenPaymentAlreadyDone() {
			// given
			Order order = seedPendingOrder();
			Payment payment = paymentRepository.saveAndFlush(Payment.ready(order));
			Long paymentId = payment.getId();
			String paymentKey = "tviva-" + UUID.randomUUID();
			LocalDateTime approvedAt = LocalDateTime.now(clock).truncatedTo(ChronoUnit.SECONDS);
			PaymentConfirmResult result = new PaymentConfirmResult(paymentKey, order.getOrderNumber(), "카드",
					order.getFinalAmount(), approvedAt);
			writer.approve(paymentId, paymentKey, result);

			// when
			writer.fail(paymentId, "뒤늦게 도착한 실패 신호");

			// then
			Payment reloaded = paymentRepository.findById(paymentId).orElseThrow();
			assertThat(reloaded.getStatus()).isEqualTo(PaymentStatus.DONE);
			assertThat(reloaded.getPaymentKey()).isEqualTo(paymentKey);
		}
	}
}
