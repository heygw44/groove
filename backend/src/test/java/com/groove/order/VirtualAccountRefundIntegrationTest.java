package com.groove.order;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.util.ReflectionTestUtils;

import com.groove.fixture.ArtistFixture;
import com.groove.fixture.MemberFixture;
import com.groove.fixture.OrderFixture;
import com.groove.fixture.PaymentFixture;
import com.groove.fixture.ProductFixture;
import com.groove.fixture.StockFixture;
import com.groove.global.common.BusinessException;
import com.groove.global.common.ErrorCode;
import com.groove.inventory.repository.StockRepository;
import com.groove.member.entity.Member;
import com.groove.member.repository.MemberRepository;
import com.groove.order.dto.AdminOrderClaimCompleteRequest;
import com.groove.order.dto.AdminOrderItemCancelRequest;
import com.groove.order.dto.OrderCancelRequest;
import com.groove.order.dto.OrderItemResponse;
import com.groove.order.dto.OrderReturnRequest;
import com.groove.order.entity.Order;
import com.groove.order.entity.OrderClaim;
import com.groove.order.entity.OrderClaimStatus;
import com.groove.order.entity.OrderItem;
import com.groove.order.entity.OrderItemStatus;
import com.groove.order.repository.OrderClaimRepository;
import com.groove.order.repository.OrderRepository;
import com.groove.order.service.AdminOrderClaimService;
import com.groove.order.service.OrderItemClaimService;
import com.groove.payment.client.PaymentClient;
import com.groove.payment.client.dto.PaymentCancelCommand;
import com.groove.payment.client.dto.PaymentCancelResult;
import com.groove.payment.client.dto.RefundAccountInfo;
import com.groove.payment.entity.PaymentStatus;
import com.groove.payment.repository.PaymentRepository;
import com.groove.product.entity.Artist;
import com.groove.product.entity.Product;
import com.groove.product.repository.AlbumRepository;
import com.groove.product.repository.ArtistRepository;
import com.groove.product.repository.ProductRepository;
import com.groove.support.IntegrationTestSupport;

/**
 * 가상계좌 결제 주문의 취소·반품은 구매자 환불계좌가 있어야 환불된다. 계좌 없이 클레임을 만들지 못하게 막고, 계좌가
 * 토스 취소 호출까지 전달되는지 검증한다. 공유 DB 라 단언은 항상 자기 id 로만 한다.
 */
class VirtualAccountRefundIntegrationTest extends IntegrationTestSupport {

	private static final BigDecimal PRICE = new BigDecimal("30000");
	private static final RefundAccountInfo ACCOUNT = new RefundAccountInfo("088", "110123456789", "홍길동");

	@Autowired
	private MemberRepository memberRepository;

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
	private OrderClaimRepository orderClaimRepository;

	@Autowired
	private PaymentRepository paymentRepository;

	@Autowired
	private OrderItemClaimService orderItemClaimService;

	@Autowired
	private AdminOrderClaimService adminOrderClaimService;

	@Autowired
	private JdbcTemplate jdbcTemplate;

	@Autowired
	private Clock clock;

	@MockitoBean
	private PaymentClient paymentClient;

	private Product saveProduct() {
		Artist artist = artistRepository.save(ArtistFixture.create());
		Product createdProduct = ProductFixture.create(artist, "VA Refund " + UUID.randomUUID(), PRICE);
		albumRepository.save(createdProduct.getAlbum());
		Product product = productRepository.save(createdProduct);
		stockRepository.saveAndFlush(StockFixture.create(product, 10));
		return product;
	}

	private Member saveMember(String prefix) {
		return memberRepository.save(MemberFixture.create(prefix + UUID.randomUUID() + "@groove.com"));
	}

	private Member saveAdmin() {
		return memberRepository.save(MemberFixture.createAdmin("admin-" + UUID.randomUUID() + "@groove.com"));
	}

	/**
	 * 가상계좌 입금이 확인된 주문을 만든다. {@code itemStatuses} 개수만큼 상품주문을 담고 각각 그 상태로 둔다.
	 * DELIVERED 는 배송완료 1시간 뒤(반품 기간 안)로 잡는다.
	 */
	private SeededOrder seedVirtualAccountOrder(Member member, OrderItemStatus... itemStatuses) {
		List<Product> products = new ArrayList<>();
		for (int i = 0; i < itemStatuses.length; i++) {
			products.add(saveProduct());
		}
		Order order = OrderFixture.createWithItems(member, products);
		order.markPaid();
		LocalDateTime now = LocalDateTime.now(clock);
		order.place(now);
		for (int i = 0; i < itemStatuses.length; i++) {
			OrderItem orderItem = order.getItems().get(i);
			if (itemStatuses[i] == OrderItemStatus.PREPARING) {
				orderItem.confirmPreparing(now);
			} else if (itemStatuses[i] == OrderItemStatus.DELIVERED) {
				ReflectionTestUtils.setField(orderItem, "status",
						OrderItemStatus.DELIVERED);
				ReflectionTestUtils.setField(orderItem, "deliveredAt",
						now.minusHours(1));
			}
		}
		Order saved = orderRepository.saveAndFlush(order);
		paymentRepository.saveAndFlush(PaymentFixture.virtualAccountApproved(saved, "toss-va-" + UUID.randomUUID()));
		List<Long> itemIds = saved.getItems().stream().map(OrderItem::getId).toList();
		return new SeededOrder(saved.getId(), itemIds);
	}

	private void stubCancelSuccess() {
		given(paymentClient.cancel(any(PaymentCancelCommand.class))).willAnswer(invocation -> {
			PaymentCancelCommand command = invocation.getArgument(0);
			return new PaymentCancelResult(command.paymentKey(), "PARTIAL_CANCELED", LocalDateTime.now(clock),
					"txn-" + command.idempotencyKey(), BigDecimal.ZERO);
		});
	}

	private OrderClaim reloadClaim(Long claimId) {
		return orderClaimRepository.findById(claimId).orElseThrow();
	}

	private OrderItemStatus reloadItemStatus(Long orderId, Long itemId) {
		return orderRepository.findWithItemsById(orderId).orElseThrow().getItems().stream()
				.filter(item -> item.getId().equals(itemId))
				.findFirst().orElseThrow().getStatus();
	}

	private PaymentStatus reloadPaymentStatus(Long orderId) {
		return paymentRepository.findByOrderId(orderId).orElseThrow().getStatus();
	}

	private int countClaims(Long itemId) {
		Integer count = jdbcTemplate.queryForObject("select count(*) from order_claim where order_item_id = ?",
				Integer.class, itemId);
		return count == null ? 0 : count;
	}

	private static OrderCancelRequest cancelRequest() {
		return new OrderCancelRequest("고객 변심",
				new OrderCancelRequest.RefundAccount(ACCOUNT.bankCode(), ACCOUNT.accountNumber(),
						ACCOUNT.holderName()));
	}

	@Nested
	@DisplayName("반품")
	class Return {

		@Test
		@DisplayName("가상계좌 반품을 환불계좌와 함께 요청해 수거 완료하면 계좌가 토스 취소에 실리고 클레임이 DONE 이 된다")
		void refundsToAccountWhenReturnCompleted() {
			// given
			Member buyer = saveMember("buyer-");
			Member admin = saveAdmin();
			SeededOrder seeded = seedVirtualAccountOrder(buyer, OrderItemStatus.DELIVERED);
			Long itemId = seeded.itemIds().get(0);
			stubCancelSuccess();

			// when
			Long claimId = orderItemClaimService.returnItem(buyer.getId(), seeded.orderId(), itemId,
					new OrderReturnRequest("단순 변심", new OrderCancelRequest.RefundAccount(ACCOUNT.bankCode(),
							ACCOUNT.accountNumber(), ACCOUNT.holderName()))).claimId();
			adminOrderClaimService.collect(admin.getId(), claimId);
			adminOrderClaimService.complete(admin.getId(), claimId, new AdminOrderClaimCompleteRequest(true));

			// then
			assertThat(reloadClaim(claimId).getStatus()).isEqualTo(OrderClaimStatus.DONE);
			assertThat(reloadItemStatus(seeded.orderId(), itemId)).isEqualTo(OrderItemStatus.RETURNED);
			ArgumentCaptor<PaymentCancelCommand> captor = ArgumentCaptor.forClass(PaymentCancelCommand.class);
			verify(paymentClient, times(1)).cancel(captor.capture());
			assertThat(captor.getValue().refundReceiveAccount()).isEqualTo(ACCOUNT);
		}

		@Test
		@DisplayName("환불계좌 없이 가상계좌 반품을 요청하면 PAYMENT_REFUND_ACCOUNT_REQUIRED 로 막히고 클레임이 생기지 않는다")
		void rejectsReturnWithoutAccount() {
			// given
			Member buyer = saveMember("buyer-");
			SeededOrder seeded = seedVirtualAccountOrder(buyer, OrderItemStatus.DELIVERED);
			Long itemId = seeded.itemIds().get(0);

			// when & then
			assertThatThrownBy(() -> orderItemClaimService.returnItem(buyer.getId(), seeded.orderId(), itemId,
					new OrderReturnRequest("단순 변심")))
					.isInstanceOf(BusinessException.class)
					.extracting("errorCode")
					.isEqualTo(ErrorCode.PAYMENT_REFUND_ACCOUNT_REQUIRED);
			assertThat(countClaims(itemId)).isZero();
			verify(paymentClient, never()).cancel(any(PaymentCancelCommand.class));
		}
	}

	@Nested
	@DisplayName("cancel()")
	class Cancel {

		@Test
		@DisplayName("두 상품 모두 환불계좌와 함께 즉시 취소하면 각각 환불되고 결제는 부분취소에서 전액취소로 간다")
		void cancelsBothItemsWithAccount() {
			// given
			Member buyer = saveMember("buyer-");
			SeededOrder seeded = seedVirtualAccountOrder(buyer, OrderItemStatus.PAID, OrderItemStatus.PAID);
			Long first = seeded.itemIds().get(0);
			Long second = seeded.itemIds().get(1);
			stubCancelSuccess();

			// when
			OrderItemResponse firstResponse = orderItemClaimService.cancel(buyer.getId(), seeded.orderId(), first,
					cancelRequest());

			// then
			assertThat(reloadItemStatus(seeded.orderId(), first)).isEqualTo(OrderItemStatus.CANCELED);
			assertThat(reloadItemStatus(seeded.orderId(), second)).isEqualTo(OrderItemStatus.PAID);
			assertThat(reloadPaymentStatus(seeded.orderId())).isEqualTo(PaymentStatus.PARTIAL_CANCELED);
			assertThat(firstResponse.refundInProgress()).isFalse();

			// when
			orderItemClaimService.cancel(buyer.getId(), seeded.orderId(), second, cancelRequest());

			// then
			assertThat(reloadItemStatus(seeded.orderId(), second)).isEqualTo(OrderItemStatus.CANCELED);
			ArgumentCaptor<PaymentCancelCommand> captor = ArgumentCaptor.forClass(PaymentCancelCommand.class);
			verify(paymentClient, times(2)).cancel(captor.capture());
			assertThat(captor.getAllValues()).extracting(PaymentCancelCommand::refundReceiveAccount)
					.containsOnly(ACCOUNT);
		}

		@Test
		@DisplayName("발주확인된 상품주문을 환불계좌 없이 취소 요청하면 PAYMENT_REFUND_ACCOUNT_REQUIRED 로 막히고 클레임이 생기지 않는다")
		void rejectsPreparingCancelWithoutAccount() {
			// given
			Member buyer = saveMember("buyer-");
			SeededOrder seeded = seedVirtualAccountOrder(buyer, OrderItemStatus.PREPARING);
			Long itemId = seeded.itemIds().get(0);

			// when & then
			assertThatThrownBy(() -> orderItemClaimService.cancel(buyer.getId(), seeded.orderId(), itemId,
					new OrderCancelRequest("고객 변심")))
					.isInstanceOf(BusinessException.class)
					.extracting("errorCode")
					.isEqualTo(ErrorCode.PAYMENT_REFUND_ACCOUNT_REQUIRED);
			assertThat(countClaims(itemId)).isZero();
			verify(paymentClient, never()).cancel(any(PaymentCancelCommand.class));
		}
	}

	@Nested
	@DisplayName("cancelItemBySale()")
	class CancelItemBySale {

		@Test
		@DisplayName("가상계좌 결제 주문의 관리자 판매취소는 PAYMENT_REFUND_ACCOUNT_REQUIRED 로 막히고 토스를 부르지 않는다")
		void rejectsSaleCancelOfVirtualAccountOrder() {
			// given
			Member buyer = saveMember("buyer-");
			Member admin = saveAdmin();
			SeededOrder seeded = seedVirtualAccountOrder(buyer, OrderItemStatus.PAID);
			Long itemId = seeded.itemIds().get(0);

			// when & then
			assertThatThrownBy(() -> adminOrderClaimService.cancelItemBySale(admin.getId(), itemId,
					new AdminOrderItemCancelRequest("재고 확인 불가")))
					.isInstanceOf(BusinessException.class)
					.extracting("errorCode")
					.isEqualTo(ErrorCode.PAYMENT_REFUND_ACCOUNT_REQUIRED);
			assertThat(countClaims(itemId)).isZero();
			assertThat(reloadItemStatus(seeded.orderId(), itemId)).isEqualTo(OrderItemStatus.PAID);
			verify(paymentClient, never()).cancel(any(PaymentCancelCommand.class));
		}
	}

	private record SeededOrder(Long orderId, List<Long> itemIds) {
	}
}
