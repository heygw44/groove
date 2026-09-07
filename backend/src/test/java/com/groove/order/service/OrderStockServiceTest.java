package com.groove.order.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.willThrow;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
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
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.dao.CannotAcquireLockException;
import org.springframework.dao.PessimisticLockingFailureException;

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
import com.groove.notification.service.RestockEvent;
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

	@Mock
	ApplicationEventPublisher eventPublisher;

	OrderStockService orderStockService;

	Order order;
	Stock stock;

	@BeforeEach
	void setUp() {
		orderStockService = new OrderStockService(stockRepository, stockHistoryRepository, eventPublisher);

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

		@Test
		@DisplayName("재고 락 대기에 실패하면 STOCK_CONFLICT 예외를 던지고 flush 하지 않는다")
		void throwsStockConflictWhenLockWaitFails() {
			// given
			given(stockRepository.findAllWithProductByProductIdInForUpdate(List.of(PRODUCT_ID)))
					.willThrow(new PessimisticLockingFailureException("lock wait timeout"));

			// when & then
			assertThatThrownBy(() -> orderStockService.deduct(order))
					.isInstanceOf(BusinessException.class)
					.extracting("errorCode")
					.isEqualTo(ErrorCode.STOCK_CONFLICT);
			verify(stockRepository, never()).flush();
		}

		@Test
		@DisplayName("재고 UPDATE flush 가 데드락으로 실패하면 STOCK_CONFLICT 예외를 던지고 이력을 남기지 않는다")
		void throwsStockConflictWhenFlushDeadlocks() {
			// given
			given(stockRepository.findAllWithProductByProductIdInForUpdate(List.of(PRODUCT_ID)))
					.willReturn(List.of(stock));
			willThrow(new CannotAcquireLockException("deadlock found")).given(stockRepository).flush();

			// when & then
			assertThatThrownBy(() -> orderStockService.deduct(order))
					.isInstanceOf(BusinessException.class)
					.extracting("errorCode")
					.isEqualTo(ErrorCode.STOCK_CONFLICT);
			verify(stockHistoryRepository, never()).save(any());
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

		@Test
		@DisplayName("재고 락 대기에 실패하면 STOCK_CONFLICT 예외를 던지고 flush 하지 않는다")
		void throwsStockConflictWhenLockWaitFails() {
			// given
			given(stockRepository.findAllWithProductByProductIdInForUpdate(List.of(PRODUCT_ID)))
					.willThrow(new PessimisticLockingFailureException("lock wait timeout"));

			// when & then
			assertThatThrownBy(() -> orderStockService.restore(order))
					.isInstanceOf(BusinessException.class)
					.extracting("errorCode")
					.isEqualTo(ErrorCode.STOCK_CONFLICT);
			verify(stockRepository, never()).flush();
		}

		@Test
		@DisplayName("재고 UPDATE flush 가 데드락으로 실패하면 STOCK_CONFLICT 예외를 던지고 이력을 남기지 않는다")
		void throwsStockConflictWhenFlushDeadlocks() {
			// given
			given(stockRepository.findAllWithProductByProductIdInForUpdate(List.of(PRODUCT_ID)))
					.willReturn(List.of(stock));
			willThrow(new CannotAcquireLockException("deadlock found")).given(stockRepository).flush();

			// when & then
			assertThatThrownBy(() -> orderStockService.restore(order))
					.isInstanceOf(BusinessException.class)
					.extracting("errorCode")
					.isEqualTo(ErrorCode.STOCK_CONFLICT);
			verify(stockHistoryRepository, never()).save(any());
		}

		@Test
		@DisplayName("취소 복구로 품절이 풀리면 재입고 이벤트를 발행한다")
		void publishesRestockEventWhenSoldOutProductRestored() {
			// given
			Product product = order.getItems().get(0).getProduct();
			Stock soldOutStock = StockFixture.create(product, 0);
			given(stockRepository.findAllWithProductByProductIdInForUpdate(List.of(PRODUCT_ID)))
					.willReturn(List.of(soldOutStock));

			// when
			orderStockService.restore(order);

			// then
			ArgumentCaptor<RestockEvent> captor = ArgumentCaptor.forClass(RestockEvent.class);
			verify(eventPublisher).publishEvent(captor.capture());
			assertThat(captor.getValue().productId()).isEqualTo(PRODUCT_ID);
			assertThat(captor.getValue().productTitle()).isEqualTo(product.getTitle());
		}

		@Test
		@DisplayName("취소 복구 후에도 재고가 남아 있던 상품이면 재입고 이벤트를 발행하지 않는다")
		void doesNotPublishRestockEventWhenProductWasNotSoldOut() {
			// given (setUp 의 stock 은 복구 전에도 수량이 10이라 품절 상태가 아니었다)
			given(stockRepository.findAllWithProductByProductIdInForUpdate(List.of(PRODUCT_ID)))
					.willReturn(List.of(stock));

			// when
			orderStockService.restore(order);

			// then
			verify(eventPublisher, never()).publishEvent(any(RestockEvent.class));
		}

		@Test
		@DisplayName("여러 상품 중 품절이었던 상품만 재입고 이벤트를 발행한다")
		void publishesRestockEventOnlyForProductsThatWereSoldOut() {
			// given
			Member member = MemberFixture.create();
			Artist artist = ArtistFixture.withId(1L);
			Product soldOutProduct = ProductFixture.withId(ProductFixture.create(artist), PRODUCT_ID);
			Product inStockProduct = ProductFixture.withId(ProductFixture.create(artist), 200L);
			Order multiItemOrder = OrderFixture.withId(
					OrderFixture.createWithItems(member, List.of(soldOutProduct, inStockProduct)), 701L);
			Stock soldOutStock = StockFixture.create(soldOutProduct, 0);
			Stock inStockStock = StockFixture.create(inStockProduct, 5);
			given(stockRepository.findAllWithProductByProductIdInForUpdate(List.of(PRODUCT_ID, 200L)))
					.willReturn(List.of(soldOutStock, inStockStock));

			// when
			orderStockService.restore(multiItemOrder);

			// then
			ArgumentCaptor<RestockEvent> captor = ArgumentCaptor.forClass(RestockEvent.class);
			verify(eventPublisher, times(1)).publishEvent(captor.capture());
			assertThat(captor.getValue().productId()).isEqualTo(PRODUCT_ID);
		}
	}
}
