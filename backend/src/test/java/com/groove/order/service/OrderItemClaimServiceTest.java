package com.groove.order.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

import com.groove.fixture.ArtistFixture;
import com.groove.fixture.MemberFixture;
import com.groove.fixture.OrderFixture;
import com.groove.fixture.ProductFixture;
import com.groove.member.entity.Member;
import com.groove.order.dto.OrderCancelRequest;
import com.groove.order.dto.OrderItemResponse;
import com.groove.order.entity.Order;
import com.groove.order.entity.OrderItem;
import com.groove.order.entity.OrderItemStatus;
import com.groove.order.repository.OrderClaimRepository;
import com.groove.order.repository.OrderItemRepository;
import com.groove.payment.client.dto.RefundAccountInfo;
import com.groove.product.entity.Artist;
import com.groove.product.entity.Product;
import com.groove.product.repository.ProductImageRepository;

@ExtendWith(MockitoExtension.class)
class OrderItemClaimServiceTest {

	private static final Long MEMBER_ID = 1L;
	private static final Long ORDER_ID = 10L;
	private static final Long ITEM_ID = 100L;
	private static final Long CLAIM_ID = 500L;
	private static final LocalDateTime NOW = LocalDateTime.of(2026, 9, 29, 12, 0);

	@Mock
	OrderClaimWriter writer;

	@Mock
	OrderClaimRefundHook refundHook;

	@Mock
	OrderItemRepository orderItemRepository;

	@Mock
	OrderClaimRepository orderClaimRepository;

	@Mock
	ProductImageRepository productImageRepository;

	OrderItemClaimService service;
	OrderItem item;

	@BeforeEach
	void setUp() {
		Clock clock = Clock.fixed(NOW.atZone(ZoneId.of("Asia/Seoul")).toInstant(), ZoneId.of("Asia/Seoul"));
		service = new OrderItemClaimService(writer, refundHook, orderItemRepository, orderClaimRepository,
				productImageRepository, clock);
		Member member = MemberFixture.create();
		Artist artist = ArtistFixture.withId(1L);
		Product product = ProductFixture.withId(ProductFixture.create(artist), 200L);
		Order order = OrderFixture.withId(OrderFixture.createWithItem(member, product, 1), ORDER_ID);
		item = order.getItems().get(0);
		ReflectionTestUtils.setField(item, "id", ITEM_ID);
		given(productImageRepository.findAllByProductIdInAndSortOrder(any(), eq(0))).willReturn(List.of());
	}

	@Nested
	@DisplayName("cancel()")
	class Cancel {

		@Test
		@DisplayName("즉시 취소 대상이면 환불을 시도한다")
		void refundsWhenImmediate() {
			// given
			ReflectionTestUtils.setField(item, "status", OrderItemStatus.CANCELED);
			given(writer.requestCancel(MEMBER_ID, ORDER_ID, ITEM_ID, "사유", null))
					.willReturn(new OrderClaimRequestResult(CLAIM_ID, ITEM_ID, ORDER_ID, new BigDecimal("10000"),
							true));
			given(orderItemRepository.findWithProductById(ITEM_ID)).willReturn(Optional.of(item));

			// when
			OrderItemResponse response = service.cancel(MEMBER_ID, ORDER_ID, ITEM_ID, new OrderCancelRequest("사유"));

			// then
			verify(refundHook).refund(ORDER_ID, CLAIM_ID, new BigDecimal("10000"), "사유", null);
			assertThat(response.status()).isEqualTo(OrderItemStatus.CANCELED);
		}

		@Test
		@DisplayName("승인 대기 요청이면 환불을 시도하지 않는다")
		void doesNotRefundWhenPending() {
			// given
			ReflectionTestUtils.setField(item, "status", OrderItemStatus.PREPARING);
			given(writer.requestCancel(MEMBER_ID, ORDER_ID, ITEM_ID, "사유", null))
					.willReturn(new OrderClaimRequestResult(CLAIM_ID, ITEM_ID, ORDER_ID, new BigDecimal("10000"),
							false));
			given(orderItemRepository.findWithProductById(ITEM_ID)).willReturn(Optional.of(item));

			// when
			service.cancel(MEMBER_ID, ORDER_ID, ITEM_ID, new OrderCancelRequest("사유"));

			// then
			verify(refundHook, never()).refund(any(), any(), any(), any(), any());
		}

		@Test
		@DisplayName("환불계좌가 있으면 훅에 변환해 전달한다")
		void convertsRefundAccount() {
			// given
			OrderCancelRequest.RefundAccount refundAccount = new OrderCancelRequest.RefundAccount("088",
					"12345678901234", "홍길동");
			OrderCancelRequest request = new OrderCancelRequest("사유", refundAccount);
			RefundAccountInfo expected = new RefundAccountInfo("088", "12345678901234", "홍길동");
			given(writer.requestCancel(MEMBER_ID, ORDER_ID, ITEM_ID, "사유", expected))
					.willReturn(new OrderClaimRequestResult(CLAIM_ID, ITEM_ID, ORDER_ID, new BigDecimal("10000"),
							true));
			given(orderItemRepository.findWithProductById(ITEM_ID)).willReturn(Optional.of(item));

			// when
			service.cancel(MEMBER_ID, ORDER_ID, ITEM_ID, request);

			// then
			verify(refundHook).refund(ORDER_ID, CLAIM_ID, new BigDecimal("10000"), "사유", expected);
		}
	}

	@Nested
	@DisplayName("returnItem()")
	class ReturnItem {

		@Test
		@DisplayName("반품 요청은 환불을 시도하지 않는다")
		void doesNotRefund() {
			// given
			given(orderItemRepository.findWithProductById(ITEM_ID)).willReturn(Optional.of(item));

			// when
			service.returnItem(MEMBER_ID, ORDER_ID, ITEM_ID, null);

			// then
			verify(refundHook, never()).refund(any(), any(), any(), any(), any());
		}
	}

	@Nested
	@DisplayName("withdraw()")
	class Withdraw {

		@Test
		@DisplayName("철회 후 대상 상품주문의 최신 상태를 반환한다")
		void returnsLatestItemState() {
			// given
			given(writer.withdraw(MEMBER_ID, CLAIM_ID)).willReturn(ITEM_ID);
			given(orderItemRepository.findWithProductById(ITEM_ID)).willReturn(Optional.of(item));

			// when
			OrderItemResponse response = service.withdraw(MEMBER_ID, CLAIM_ID);

			// then
			assertThat(response.productOrderNumber()).isEqualTo(item.getProductOrderNumber());
		}

		@Test
		@DisplayName("응답에 상품주문 id 를 싣고, REQUESTED 클레임이 남아 있으면 claimId 를 채운다")
		void populatesIdAndClaimId() {
			// given
			given(writer.withdraw(MEMBER_ID, CLAIM_ID)).willReturn(ITEM_ID);
			given(orderItemRepository.findWithProductById(ITEM_ID)).willReturn(Optional.of(item));
			given(orderClaimRepository.findRequestedClaimIdsByOrderItemId(List.of(ITEM_ID)))
					.willReturn(Map.of(ITEM_ID, 501L));

			// when
			OrderItemResponse response = service.withdraw(MEMBER_ID, CLAIM_ID);

			// then
			assertThat(response.id()).isEqualTo(ITEM_ID);
			assertThat(response.claimId()).isEqualTo(501L);
		}

		@Test
		@DisplayName("REQUESTED 클레임이 없으면 claimId 는 null 이다")
		void claimIdNullWhenNoRequestedClaim() {
			// given
			given(writer.withdraw(MEMBER_ID, CLAIM_ID)).willReturn(ITEM_ID);
			given(orderItemRepository.findWithProductById(ITEM_ID)).willReturn(Optional.of(item));

			// when
			OrderItemResponse response = service.withdraw(MEMBER_ID, CLAIM_ID);

			// then
			assertThat(response.claimId()).isNull();
		}
	}
}
