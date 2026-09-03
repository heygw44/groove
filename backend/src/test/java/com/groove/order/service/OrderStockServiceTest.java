package com.groove.order.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

import java.util.List;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InOrder;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import com.groove.fixture.ArtistFixture;
import com.groove.fixture.MemberFixture;
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
import com.groove.member.entity.Member;
import com.groove.order.entity.Order;
import com.groove.product.entity.Artist;
import com.groove.product.entity.Product;

@ExtendWith(MockitoExtension.class)
class OrderStockServiceTest {

	private static final Long PRODUCT_ID = 100L;

	@Mock
	StockRepository stockRepository;

	@Mock
	StockHistoryRepository stockHistoryRepository;

	OrderStockService orderStockService;

	Order order;
	Stock stock;

	@BeforeEach
	void setUp() {
		orderStockService = new OrderStockService(stockRepository, stockHistoryRepository);

		Member member = MemberFixture.create();
		Artist artist = ArtistFixture.withId(1L);
		Product product = ProductFixture.withId(ProductFixture.create(artist), PRODUCT_ID);
		order = OrderFixture.withId(OrderFixture.createWithItem(member, product, 2), 700L);
		stock = StockFixture.create(product, 10);
	}

	@Nested
	@DisplayName("deduct()")
	class Deduct {

		@Test
		@DisplayName("재고를 감소시킨 뒤 flush 하고 OUT 이력을 남긴다")
		void decreasesStockAndRecordsOutHistoryAfterFlush() {
			// given
			given(stockRepository.findAllWithProductByProductIdInForUpdate(List.of(PRODUCT_ID)))
					.willReturn(List.of(stock));

			// when
			orderStockService.deduct(order);

			// then
			assertThat(stock.getQuantity()).isEqualTo(8);
			InOrder inOrder = inOrder(stockRepository, stockHistoryRepository);
			inOrder.verify(stockRepository).flush();
			ArgumentCaptor<StockHistory> captor = ArgumentCaptor.forClass(StockHistory.class);
			inOrder.verify(stockHistoryRepository).save(captor.capture());
			assertThat(captor.getValue().getChangeType()).isEqualTo(StockChangeType.OUT);
			assertThat(captor.getValue().getQuantityDelta()).isEqualTo(-2);
		}

		@Test
		@DisplayName("재고보다 많은 수량이면 STOCK_INSUFFICIENT 예외를 던지고 이력을 남기지 않는다")
		void throwsWhenStockInsufficient() {
			// given
			Stock shortStock = StockFixture.create(order.getItems().get(0).getProduct(), 1);
			given(stockRepository.findAllWithProductByProductIdInForUpdate(List.of(PRODUCT_ID)))
					.willReturn(List.of(shortStock));

			// when & then
			assertThatThrownBy(() -> orderStockService.deduct(order))
					.isInstanceOf(BusinessException.class)
					.extracting("errorCode")
					.isEqualTo(ErrorCode.STOCK_INSUFFICIENT);
			verify(stockHistoryRepository, never()).save(any());
		}

		@Test
		@DisplayName("재고가 일부 상품에 대해 없으면 STOCK_NOT_FOUND 예외를 던진다")
		void throwsWhenStockMissing() {
			// given
			given(stockRepository.findAllWithProductByProductIdInForUpdate(List.of(PRODUCT_ID)))
					.willReturn(List.of());

			// when & then
			assertThatThrownBy(() -> orderStockService.deduct(order))
					.isInstanceOf(BusinessException.class)
					.extracting("errorCode")
					.isEqualTo(ErrorCode.STOCK_NOT_FOUND);
			verify(stockRepository, never()).flush();
		}
	}

	@Nested
	@DisplayName("restore()")
	class Restore {

		@Test
		@DisplayName("재고를 증가시킨 뒤 flush 하고 CANCEL 이력을 남긴다")
		void increasesStockAndRecordsCancelHistoryAfterFlush() {
			// given
			given(stockRepository.findAllWithProductByProductIdInForUpdate(List.of(PRODUCT_ID)))
					.willReturn(List.of(stock));

			// when
			orderStockService.restore(order);

			// then
			assertThat(stock.getQuantity()).isEqualTo(12);
			InOrder inOrder = inOrder(stockRepository, stockHistoryRepository);
			inOrder.verify(stockRepository).flush();
			ArgumentCaptor<StockHistory> captor = ArgumentCaptor.forClass(StockHistory.class);
			inOrder.verify(stockHistoryRepository).save(captor.capture());
			assertThat(captor.getValue().getChangeType()).isEqualTo(StockChangeType.CANCEL);
			assertThat(captor.getValue().getQuantityDelta()).isEqualTo(2);
		}

		@Test
		@DisplayName("재고가 일부 상품에 대해 없으면 STOCK_NOT_FOUND 예외를 던진다")
		void throwsWhenStockMissing() {
			// given
			given(stockRepository.findAllWithProductByProductIdInForUpdate(List.of(PRODUCT_ID)))
					.willReturn(List.of());

			// when & then
			assertThatThrownBy(() -> orderStockService.restore(order))
					.isInstanceOf(BusinessException.class)
					.extracting("errorCode")
					.isEqualTo(ErrorCode.STOCK_NOT_FOUND);
			verify(stockRepository, never()).flush();
		}
	}
}
