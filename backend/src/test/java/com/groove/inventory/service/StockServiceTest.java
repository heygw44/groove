package com.groove.inventory.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

import java.util.Optional;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import com.groove.fixture.ArtistFixture;
import com.groove.fixture.ProductFixture;
import com.groove.fixture.StockFixture;
import com.groove.global.common.BusinessException;
import com.groove.global.common.ErrorCode;
import com.groove.inventory.dto.StockAdjustRequest;
import com.groove.inventory.dto.StockResponse;
import com.groove.inventory.entity.Stock;
import com.groove.inventory.entity.StockChangeType;
import com.groove.inventory.entity.StockHistory;
import com.groove.inventory.repository.StockHistoryRepository;
import com.groove.inventory.repository.StockRepository;
import com.groove.product.entity.Artist;
import com.groove.product.entity.Product;

@ExtendWith(MockitoExtension.class)
class StockServiceTest {

	private static final Long PRODUCT_ID = 1L;

	@Mock
	StockRepository stockRepository;

	@Mock
	StockHistoryRepository stockHistoryRepository;

	StockService stockService;

	@BeforeEach
	void setUp() {
		stockService = new StockService(stockRepository, stockHistoryRepository);
	}

	@Nested
	@DisplayName("create()")
	class Create {

		@Test
		@DisplayName("초기 수량이 0이면 이력을 남기지 않는다")
		void doesNotRecordHistoryWhenInitialQuantityZero() {
			// given
			Artist artist = ArtistFixture.create();
			Product product = ProductFixture.create(artist);
			given(stockRepository.save(any())).willAnswer(invocation -> invocation.getArgument(0));

			// when
			Stock stock = stockService.create(product, 0);

			// then
			assertThat(stock.getQuantity()).isEqualTo(0);
			verify(stockHistoryRepository, never()).save(any());
		}

		@Test
		@DisplayName("초기 수량이 0보다 크면 IN 이력을 남긴다")
		void recordsInHistoryWhenInitialQuantityPositive() {
			// given
			Artist artist = ArtistFixture.create();
			Product product = ProductFixture.create(artist);
			given(stockRepository.save(any())).willAnswer(invocation -> invocation.getArgument(0));

			// when
			Stock stock = stockService.create(product, 10);

			// then
			assertThat(stock.getQuantity()).isEqualTo(10);
			ArgumentCaptor<StockHistory> captor = ArgumentCaptor.forClass(StockHistory.class);
			verify(stockHistoryRepository).save(captor.capture());
			assertThat(captor.getValue().getChangeType()).isEqualTo(StockChangeType.IN);
			assertThat(captor.getValue().getQuantityDelta()).isEqualTo(10);
		}
	}

	@Nested
	@DisplayName("adjust()")
	class Adjust {

		@Test
		@DisplayName("존재하지 않는 상품이면 STOCK_NOT_FOUND 예외를 던진다")
		void throwsWhenStockNotFound() {
			// given
			given(stockRepository.findWithProductByProductId(PRODUCT_ID)).willReturn(Optional.empty());
			StockAdjustRequest request = StockFixture.adjustRequest(StockChangeType.IN, 5);

			// when & then
			assertThatThrownBy(() -> stockService.adjust(PRODUCT_ID, request))
					.isInstanceOf(BusinessException.class)
					.extracting("errorCode")
					.isEqualTo(ErrorCode.STOCK_NOT_FOUND);
		}

		@Test
		@DisplayName("IN 이면 수량을 늘리고 양수 delta 이력을 남긴다")
		void increasesQuantityAndRecordsPositiveDelta() {
			// given
			Product product = ProductFixture.create(ArtistFixture.create());
			Stock stock = StockFixture.create(product, 10);
			given(stockRepository.findWithProductByProductId(PRODUCT_ID)).willReturn(Optional.of(stock));
			StockAdjustRequest request = StockFixture.adjustRequest(StockChangeType.IN, 5);

			// when
			StockResponse response = stockService.adjust(PRODUCT_ID, request);

			// then
			assertThat(response.quantity()).isEqualTo(15);
			ArgumentCaptor<StockHistory> captor = ArgumentCaptor.forClass(StockHistory.class);
			verify(stockHistoryRepository).save(captor.capture());
			assertThat(captor.getValue().getChangeType()).isEqualTo(StockChangeType.IN);
			assertThat(captor.getValue().getQuantityDelta()).isEqualTo(5);
		}

		@Test
		@DisplayName("OUT 이면 수량을 줄이고 음수 delta 이력을 남긴다")
		void decreasesQuantityAndRecordsNegativeDelta() {
			// given
			Product product = ProductFixture.create(ArtistFixture.create());
			Stock stock = StockFixture.create(product, 10);
			given(stockRepository.findWithProductByProductId(PRODUCT_ID)).willReturn(Optional.of(stock));
			StockAdjustRequest request = StockFixture.adjustRequest(StockChangeType.OUT, 4);

			// when
			StockResponse response = stockService.adjust(PRODUCT_ID, request);

			// then
			assertThat(response.quantity()).isEqualTo(6);
			ArgumentCaptor<StockHistory> captor = ArgumentCaptor.forClass(StockHistory.class);
			verify(stockHistoryRepository).save(captor.capture());
			assertThat(captor.getValue().getChangeType()).isEqualTo(StockChangeType.OUT);
			assertThat(captor.getValue().getQuantityDelta()).isEqualTo(-4);
		}

		@Test
		@DisplayName("OUT 인데 재고가 부족하면 STOCK_INSUFFICIENT 예외를 던진다")
		void throwsWhenOutInsufficient() {
			// given
			Product product = ProductFixture.create(ArtistFixture.create());
			Stock stock = StockFixture.create(product, 1);
			given(stockRepository.findWithProductByProductId(PRODUCT_ID)).willReturn(Optional.of(stock));
			StockAdjustRequest request = StockFixture.adjustRequest(StockChangeType.OUT, 5);

			// when & then
			assertThatThrownBy(() -> stockService.adjust(PRODUCT_ID, request))
					.isInstanceOf(BusinessException.class)
					.extracting("errorCode")
					.isEqualTo(ErrorCode.STOCK_INSUFFICIENT);
			verify(stockHistoryRepository, never()).save(any());
		}

		@Test
		@DisplayName("ADJUST 면 절대값으로 지정하고 현재값과의 차이를 delta 로 남긴다")
		void replacesQuantityAndRecordsDifferenceAsDelta() {
			// given
			Product product = ProductFixture.create(ArtistFixture.create());
			Stock stock = StockFixture.create(product, 10);
			given(stockRepository.findWithProductByProductId(PRODUCT_ID)).willReturn(Optional.of(stock));
			StockAdjustRequest request = StockFixture.adjustRequest(StockChangeType.ADJUST, 3);

			// when
			StockResponse response = stockService.adjust(PRODUCT_ID, request);

			// then
			assertThat(response.quantity()).isEqualTo(3);
			ArgumentCaptor<StockHistory> captor = ArgumentCaptor.forClass(StockHistory.class);
			verify(stockHistoryRepository).save(captor.capture());
			assertThat(captor.getValue().getChangeType()).isEqualTo(StockChangeType.ADJUST);
			assertThat(captor.getValue().getQuantityDelta()).isEqualTo(-7);
		}

		@Test
		@DisplayName("ADJUST 로 동일 값을 지정해도 delta 0 이력을 남긴다")
		void recordsZeroDeltaHistoryWhenAdjustToSameValue() {
			// given
			Product product = ProductFixture.create(ArtistFixture.create());
			Stock stock = StockFixture.create(product, 10);
			given(stockRepository.findWithProductByProductId(PRODUCT_ID)).willReturn(Optional.of(stock));
			StockAdjustRequest request = StockFixture.adjustRequest(StockChangeType.ADJUST, 10);

			// when
			stockService.adjust(PRODUCT_ID, request);

			// then
			ArgumentCaptor<StockHistory> captor = ArgumentCaptor.forClass(StockHistory.class);
			verify(stockHistoryRepository).save(captor.capture());
			assertThat(captor.getValue().getQuantityDelta()).isEqualTo(0);
		}

		@Test
		@DisplayName("CANCEL 이면 COMMON_INVALID_INPUT 예외를 던진다")
		void throwsWhenCancel() {
			// given
			Product product = ProductFixture.create(ArtistFixture.create());
			Stock stock = StockFixture.create(product, 10);
			given(stockRepository.findWithProductByProductId(PRODUCT_ID)).willReturn(Optional.of(stock));
			StockAdjustRequest request = StockFixture.adjustRequest(StockChangeType.CANCEL, 1);

			// when & then
			assertThatThrownBy(() -> stockService.adjust(PRODUCT_ID, request))
					.isInstanceOf(BusinessException.class)
					.extracting("errorCode")
					.isEqualTo(ErrorCode.COMMON_INVALID_INPUT);
			verify(stockHistoryRepository, never()).save(any());
		}
	}

	@Nested
	@DisplayName("getByProductId()")
	class GetByProductId {

		@Test
		@DisplayName("존재하는 재고면 응답을 반환한다")
		void returnsStockResponse() {
			// given
			Product product = ProductFixture.create(ArtistFixture.create());
			Stock stock = StockFixture.create(product, 7);
			given(stockRepository.findWithProductByProductId(PRODUCT_ID)).willReturn(Optional.of(stock));

			// when
			StockResponse response = stockService.getByProductId(PRODUCT_ID);

			// then
			assertThat(response.quantity()).isEqualTo(7);
		}

		@Test
		@DisplayName("존재하지 않으면 STOCK_NOT_FOUND 예외를 던진다")
		void throwsWhenNotFound() {
			// given
			given(stockRepository.findWithProductByProductId(PRODUCT_ID)).willReturn(Optional.empty());

			// when & then
			assertThatThrownBy(() -> stockService.getByProductId(PRODUCT_ID))
					.isInstanceOf(BusinessException.class)
					.extracting("errorCode")
					.isEqualTo(ErrorCode.STOCK_NOT_FOUND);
		}
	}
}
