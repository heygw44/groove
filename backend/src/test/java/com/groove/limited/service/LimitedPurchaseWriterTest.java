package com.groove.limited.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

import java.time.Clock;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.Optional;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.dao.DataIntegrityViolationException;

import com.groove.fixture.AddressFixture;
import com.groove.fixture.ArtistFixture;
import com.groove.fixture.LimitedDropFixture;
import com.groove.fixture.LimitedPurchaseFixture;
import com.groove.fixture.MemberFixture;
import com.groove.fixture.ProductFixture;
import com.groove.fixture.StockFixture;
import com.groove.global.common.BusinessException;
import com.groove.global.common.ErrorCode;
import com.groove.inventory.repository.StockHistoryRepository;
import com.groove.inventory.repository.StockRepository;
import com.groove.limited.dto.LimitedPurchaseResponse;
import com.groove.limited.entity.LimitedDrop;
import com.groove.limited.entity.LimitedPurchase;
import com.groove.limited.repository.LimitedDropRepository;
import com.groove.limited.repository.LimitedPurchaseRepository;
import com.groove.member.entity.Address;
import com.groove.member.entity.Member;
import com.groove.member.repository.AddressRepository;
import com.groove.member.repository.MemberRepository;
import com.groove.order.repository.OrderRepository;
import com.groove.order.service.OrderNumberGenerator;
import com.groove.product.entity.Artist;
import com.groove.product.entity.Product;

@ExtendWith(MockitoExtension.class)
class LimitedPurchaseWriterTest {

	private static final ZoneId ZONE = ZoneId.of("Asia/Seoul");

	@Mock
	private LimitedDropRepository limitedDropRepository;

	@Mock
	private LimitedPurchaseRepository limitedPurchaseRepository;

	@Mock
	private MemberRepository memberRepository;

	@Mock
	private AddressRepository addressRepository;

	@Mock
	private StockRepository stockRepository;

	@Mock
	private StockHistoryRepository stockHistoryRepository;

	@Mock
	private OrderRepository orderRepository;

	@Mock
	private OrderNumberGenerator orderNumberGenerator;

	private Clock clock;

	private LimitedPurchaseWriter limitedPurchaseWriter;

	@BeforeEach
	void setUp() {
		clock = Clock.fixed(Instant.parse("2026-09-04T03:00:00Z"), ZONE);
		limitedPurchaseWriter = new LimitedPurchaseWriter(limitedDropRepository, limitedPurchaseRepository,
				memberRepository, addressRepository, stockRepository, stockHistoryRepository, orderRepository,
				orderNumberGenerator, clock);
	}

	@Nested
	@DisplayName("write()")
	class Write {

		@Test
		@DisplayName("드롭을 찾을 수 없으면 LIMITED_DROP_NOT_FOUND 예외를 던진다")
		void throwsWhenDropNotFound() {
			// given
			given(limitedDropRepository.findByIdForUpdate(1L)).willReturn(Optional.empty());

			// when & then
			assertThatThrownBy(() -> limitedPurchaseWriter.write(1L, 10L, 20L))
					.isInstanceOf(BusinessException.class)
					.extracting("errorCode")
					.isEqualTo(ErrorCode.LIMITED_DROP_NOT_FOUND);
		}

		@Test
		@DisplayName("uk_limited_purchase 위반이면 LIMITED_ALREADY_PURCHASED 예외를 던진다")
		void throwsWhenDuplicatePurchase() {
			// given
			Product product = product();
			LimitedDrop drop = openDrop(product, 2L);
			Member member = MemberFixture.withId(MemberFixture.create(), 10L);
			Address address = AddressFixture.withId(AddressFixture.create(member), 20L);
			given(limitedDropRepository.findByIdForUpdate(2L)).willReturn(Optional.of(drop));
			given(memberRepository.findById(10L)).willReturn(Optional.of(member));
			given(addressRepository.findByIdAndMemberId(20L, 10L)).willReturn(Optional.of(address));
			given(limitedPurchaseRepository.saveAndFlush(any()))
					.willThrow(new DataIntegrityViolationException("duplicate"));

			// when & then
			assertThatThrownBy(() -> limitedPurchaseWriter.write(2L, 10L, 20L))
					.isInstanceOf(BusinessException.class)
					.extracting("errorCode")
					.isEqualTo(ErrorCode.LIMITED_ALREADY_PURCHASED);
			verify(stockRepository, never()).decreaseIfAvailable(anyLong(), anyInt());
		}

		@Test
		@DisplayName("재고 조건부 UPDATE 가 0행이면 LIMITED_SOLD_OUT 예외를 던진다")
		void throwsWhenStockUpdateAffectsNoRow() {
			// given
			Product product = product();
			LimitedDrop drop = openDrop(product, 3L);
			Member member = MemberFixture.withId(MemberFixture.create(), 10L);
			Address address = AddressFixture.withId(AddressFixture.create(member), 20L);
			given(limitedDropRepository.findByIdForUpdate(3L)).willReturn(Optional.of(drop));
			given(memberRepository.findById(10L)).willReturn(Optional.of(member));
			given(addressRepository.findByIdAndMemberId(20L, 10L)).willReturn(Optional.of(address));
			given(limitedPurchaseRepository.saveAndFlush(any())).willAnswer(invocation -> invocation.getArgument(0));
			given(stockRepository.decreaseIfAvailable(product.getId(), 1)).willReturn(0);

			// when & then
			assertThatThrownBy(() -> limitedPurchaseWriter.write(3L, 10L, 20L))
					.isInstanceOf(BusinessException.class)
					.extracting("errorCode")
					.isEqualTo(ErrorCode.LIMITED_SOLD_OUT);
			verify(orderRepository, never()).save(any());
		}

		@Test
		@DisplayName("정상 흐름이면 주문을 만들고 구매 이력에 붙이고 판매량을 반영한다")
		void createsOrderAndAttachesPurchaseOnSuccess() {
			// given
			Product product = product();
			LimitedDrop drop = openDrop(product, 4L);
			Member member = MemberFixture.withId(MemberFixture.create(), 10L);
			Address address = AddressFixture.withId(AddressFixture.create(member), 20L);
			given(limitedDropRepository.findByIdForUpdate(4L)).willReturn(Optional.of(drop));
			given(memberRepository.findById(10L)).willReturn(Optional.of(member));
			given(addressRepository.findByIdAndMemberId(20L, 10L)).willReturn(Optional.of(address));
			given(limitedPurchaseRepository.saveAndFlush(any())).willAnswer(invocation -> invocation.getArgument(0));
			given(stockRepository.decreaseIfAvailable(product.getId(), 1)).willReturn(1);
			given(stockRepository.findByProductId(product.getId()))
					.willReturn(Optional.of(StockFixture.withId(StockFixture.create(product, 9), 30L)));
			given(orderNumberGenerator.generate()).willReturn("20260904-ABCDE123");

			// when
			LimitedPurchaseResponse response = limitedPurchaseWriter.write(4L, 10L, 20L);

			// then
			assertThat(response.orderNumber()).isEqualTo("20260904-ABCDE123");
			assertThat(drop.getSoldCount()).isEqualTo(1);
			verify(orderRepository).save(any());
			verify(stockHistoryRepository).save(any());
		}
	}

	@Nested
	@DisplayName("revertByOrder()")
	class RevertByOrder {

		@Test
		@DisplayName("한정반 주문이 아니면 empty 를 반환하고 락도 삭제도 하지 않는다")
		void returnsEmptyWhenNotLimitedOrder() {
			// given
			given(limitedPurchaseRepository.findByOrderId(100L)).willReturn(Optional.empty());

			// when
			Optional<LimitedRelease> result = limitedPurchaseWriter.revertByOrder(100L, LocalDateTime.now(clock));

			// then
			assertThat(result).isEmpty();
			verify(limitedDropRepository, never()).findByIdForUpdate(any());
			verify(limitedPurchaseRepository, never()).delete(any());
		}

		@Test
		@DisplayName("한정반 주문이면 드롭을 락으로 잠그고 판매량을 되돌린 뒤 구매 이력을 지운다")
		void restoresSaleAndDeletesPurchase() {
			// given
			Product product = product();
			LimitedDrop drop = openDrop(product, 5L);
			Member member = MemberFixture.withId(MemberFixture.create(), 10L);
			LimitedPurchase purchase = LimitedPurchaseFixture.create(drop, member);
			drop.recordSale(1);
			given(limitedPurchaseRepository.findByOrderId(100L)).willReturn(Optional.of(purchase));
			given(limitedDropRepository.findByIdForUpdate(5L)).willReturn(Optional.of(drop));

			// when
			Optional<LimitedRelease> result = limitedPurchaseWriter.revertByOrder(100L, LocalDateTime.now(clock));

			// then
			assertThat(result).contains(new LimitedRelease(5L, 10L));
			assertThat(drop.getSoldCount()).isZero();
			verify(limitedDropRepository).findByIdForUpdate(5L);
			verify(limitedPurchaseRepository).delete(purchase);
		}

		@Test
		@DisplayName("락을 잡는 시점에 드롭이 없으면 LIMITED_DROP_NOT_FOUND 예외를 던진다")
		void throwsWhenDropNotFoundUnderLock() {
			// given
			Product product = product();
			LimitedDrop drop = openDrop(product, 5L);
			Member member = MemberFixture.withId(MemberFixture.create(), 10L);
			LimitedPurchase purchase = LimitedPurchaseFixture.create(drop, member);
			given(limitedPurchaseRepository.findByOrderId(100L)).willReturn(Optional.of(purchase));
			given(limitedDropRepository.findByIdForUpdate(5L)).willReturn(Optional.empty());

			// when & then
			assertThatThrownBy(() -> limitedPurchaseWriter.revertByOrder(100L, LocalDateTime.now(clock)))
					.isInstanceOf(BusinessException.class)
					.extracting("errorCode")
					.isEqualTo(ErrorCode.LIMITED_DROP_NOT_FOUND);
			verify(limitedPurchaseRepository, never()).delete(any());
		}
	}

	private LimitedDrop openDrop(Product product, Long id) {
		LimitedDrop drop = LimitedDropFixture.withId(LimitedDropFixture.open(product, 10), id);
		java.time.LocalDateTime now = java.time.LocalDateTime.now(clock);
		LimitedDropFixture.withOpenAt(drop, now.minusHours(1));
		LimitedDropFixture.withCloseAt(drop, now.plusHours(1));
		return drop;
	}

	private static Product product() {
		Artist artist = ArtistFixture.withId(1L);
		return ProductFixture.withId(ProductFixture.create(artist), 100L);
	}
}
