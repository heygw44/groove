package com.groove.limited.entity;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.LocalDateTime;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.EnumSource;

import com.groove.fixture.ArtistFixture;
import com.groove.fixture.LimitedDropFixture;
import com.groove.fixture.ProductFixture;
import com.groove.global.common.BusinessException;
import com.groove.global.common.ErrorCode;
import com.groove.product.entity.Artist;
import com.groove.product.entity.Product;

class LimitedDropTest {

	private final Artist artist = ArtistFixture.withId(1L);
	private final Product product = ProductFixture.withId(ProductFixture.create(artist), 100L);

	@Nested
	@DisplayName("schedule()")
	class Schedule {

		@Test
		@DisplayName("생성하면 SCHEDULED 상태이고 판매 수량은 0이다")
		void createsWithScheduledStatusAndZeroSoldCount() {
			// given & when
			LimitedDrop drop = LimitedDropFixture.scheduled(product);

			// then
			assertThat(drop.getStatus()).isEqualTo(LimitedDropStatus.SCHEDULED);
			assertThat(drop.getSoldCount()).isZero();
		}

		@Test
		@DisplayName("회원당 구매 한도를 지정하지 않으면 1로 저장된다")
		void defaultsPerMemberLimitToOneWhenNull() {
			// given & when
			LimitedDrop drop = LimitedDrop.schedule(product, 100, null,
					LocalDateTime.now().plusDays(1), LocalDateTime.now().plusDays(2));

			// then
			assertThat(drop.getPerMemberLimit()).isEqualTo(1);
		}

		@ParameterizedTest
		@CsvSource({
			"0, 1",
			"-1, 1",
			"10, 0",
			"10, -1",
			"10, 11"
		})
		@DisplayName("수량 조합이 올바르지 않으면 COMMON_INVALID_INPUT 예외를 던진다")
		void throwsWhenQuantityInvalid(int totalQuantity, int perMemberLimit) {
			// when & then
			assertThatThrownBy(() -> LimitedDrop.schedule(product, totalQuantity, perMemberLimit,
					LocalDateTime.now().plusDays(1), LocalDateTime.now().plusDays(2)))
					.isInstanceOf(BusinessException.class)
					.extracting("errorCode")
					.isEqualTo(ErrorCode.COMMON_INVALID_INPUT);
		}

		@Test
		@DisplayName("오픈 시각이 마감 시각보다 이후가 아니면 COMMON_INVALID_INPUT 예외를 던진다")
		void throwsWhenOpenAtNotBeforeCloseAt() {
			// given
			LocalDateTime openAt = LocalDateTime.now().plusDays(2);
			LocalDateTime closeAt = LocalDateTime.now().plusDays(1);

			// when & then
			assertThatThrownBy(() -> LimitedDrop.schedule(product, 100, 1, openAt, closeAt))
					.isInstanceOf(BusinessException.class)
					.extracting("errorCode")
					.isEqualTo(ErrorCode.COMMON_INVALID_INPUT);
		}

		@Test
		@DisplayName("오픈 시각이 과거면 COMMON_INVALID_INPUT 예외를 던진다")
		void throwsWhenOpenAtInPast() {
			// given
			LocalDateTime openAt = LocalDateTime.now().minusDays(1);
			LocalDateTime closeAt = LocalDateTime.now().plusDays(1);

			// when & then
			assertThatThrownBy(() -> LimitedDrop.schedule(product, 100, 1, openAt, closeAt))
					.isInstanceOf(BusinessException.class)
					.extracting("errorCode")
					.isEqualTo(ErrorCode.COMMON_INVALID_INPUT);
		}
	}

	@Nested
	@DisplayName("open()")
	class Open {

		@Test
		@DisplayName("SCHEDULED 상태면 OPEN 으로 바뀐다")
		void changesToOpenWhenScheduled() {
			// given
			LimitedDrop drop = LimitedDropFixture.scheduled(product);

			// when
			drop.open();

			// then
			assertThat(drop.getStatus()).isEqualTo(LimitedDropStatus.OPEN);
		}

		@ParameterizedTest
		@EnumSource(value = LimitedDropStatus.class, names = "SCHEDULED", mode = EnumSource.Mode.EXCLUDE)
		@DisplayName("SCHEDULED 상태가 아니면 LIMITED_INVALID_STATUS 예외를 던진다")
		void throwsWhenNotScheduled(LimitedDropStatus status) {
			// given
			LimitedDrop drop = LimitedDropFixture.withStatus(LimitedDropFixture.scheduled(product), status);

			// when & then
			assertThatThrownBy(drop::open)
					.isInstanceOf(BusinessException.class)
					.extracting("errorCode")
					.isEqualTo(ErrorCode.LIMITED_INVALID_STATUS);
		}
	}

	@Nested
	@DisplayName("markSoldOut()")
	class MarkSoldOut {

		@Test
		@DisplayName("OPEN 상태면 SOLD_OUT 으로 바뀐다")
		void changesToSoldOutWhenOpen() {
			// given
			LimitedDrop drop = LimitedDropFixture.open(product, 10);

			// when
			drop.markSoldOut();

			// then
			assertThat(drop.getStatus()).isEqualTo(LimitedDropStatus.SOLD_OUT);
		}

		@ParameterizedTest
		@EnumSource(value = LimitedDropStatus.class, names = "OPEN", mode = EnumSource.Mode.EXCLUDE)
		@DisplayName("OPEN 상태가 아니면 LIMITED_INVALID_STATUS 예외를 던진다")
		void throwsWhenNotOpen(LimitedDropStatus status) {
			// given
			LimitedDrop drop = LimitedDropFixture.withStatus(LimitedDropFixture.scheduled(product), status);

			// when & then
			assertThatThrownBy(drop::markSoldOut)
					.isInstanceOf(BusinessException.class)
					.extracting("errorCode")
					.isEqualTo(ErrorCode.LIMITED_INVALID_STATUS);
		}
	}

	@Nested
	@DisplayName("close()")
	class Close {

		@ParameterizedTest
		@EnumSource(value = LimitedDropStatus.class, names = {"OPEN", "SOLD_OUT"})
		@DisplayName("OPEN 또는 SOLD_OUT 상태면 CLOSED 로 바뀐다")
		void changesToClosedWhenOpenOrSoldOut(LimitedDropStatus status) {
			// given
			LimitedDrop drop = LimitedDropFixture.withStatus(LimitedDropFixture.scheduled(product), status);

			// when
			drop.close();

			// then
			assertThat(drop.getStatus()).isEqualTo(LimitedDropStatus.CLOSED);
		}

		@ParameterizedTest
		@EnumSource(value = LimitedDropStatus.class, names = {"OPEN", "SOLD_OUT"}, mode = EnumSource.Mode.EXCLUDE)
		@DisplayName("OPEN·SOLD_OUT 이 아니면 LIMITED_INVALID_STATUS 예외를 던진다")
		void throwsWhenNotOpenOrSoldOut(LimitedDropStatus status) {
			// given
			LimitedDrop drop = LimitedDropFixture.withStatus(LimitedDropFixture.scheduled(product), status);

			// when & then
			assertThatThrownBy(drop::close)
					.isInstanceOf(BusinessException.class)
					.extracting("errorCode")
					.isEqualTo(ErrorCode.LIMITED_INVALID_STATUS);
		}
	}

	@Nested
	@DisplayName("validatePurchasable()")
	class ValidatePurchasable {

		@Test
		@DisplayName("CLOSED 상태면 LIMITED_CLOSED 예외를 던진다")
		void throwsClosedWhenStatusClosed() {
			// given
			LimitedDrop drop = LimitedDropFixture.withStatus(LimitedDropFixture.open(product, 10),
					LimitedDropStatus.CLOSED);

			// when & then
			assertThatThrownBy(() -> drop.validatePurchasable(LocalDateTime.now()))
					.isInstanceOf(BusinessException.class)
					.extracting("errorCode")
					.isEqualTo(ErrorCode.LIMITED_CLOSED);
		}

		@Test
		@DisplayName("현재 시각이 마감 시각과 같거나 지났으면 LIMITED_CLOSED 예외를 던진다")
		void throwsClosedWhenNowReachedCloseAt() {
			// given
			LimitedDrop drop = LimitedDropFixture.open(product, 10);
			LocalDateTime closeAt = drop.getCloseAt();

			// when & then
			assertThatThrownBy(() -> drop.validatePurchasable(closeAt))
					.isInstanceOf(BusinessException.class)
					.extracting("errorCode")
					.isEqualTo(ErrorCode.LIMITED_CLOSED);
		}

		@Test
		@DisplayName("SCHEDULED 상태면 LIMITED_NOT_OPEN 예외를 던진다")
		void throwsNotOpenWhenScheduled() {
			// given
			LimitedDrop drop = LimitedDropFixture.scheduled(product);

			// when & then
			assertThatThrownBy(() -> drop.validatePurchasable(drop.getOpenAt().plusMinutes(1)))
					.isInstanceOf(BusinessException.class)
					.extracting("errorCode")
					.isEqualTo(ErrorCode.LIMITED_NOT_OPEN);
		}

		@Test
		@DisplayName("현재 시각이 오픈 시각보다 이전이면 LIMITED_NOT_OPEN 예외를 던진다")
		void throwsNotOpenWhenBeforeOpenAt() {
			// given
			LimitedDrop drop = LimitedDropFixture.open(product, 10);

			// when & then
			assertThatThrownBy(() -> drop.validatePurchasable(drop.getOpenAt().minusMinutes(1)))
					.isInstanceOf(BusinessException.class)
					.extracting("errorCode")
					.isEqualTo(ErrorCode.LIMITED_NOT_OPEN);
		}

		@Test
		@DisplayName("SOLD_OUT 상태면 LIMITED_SOLD_OUT 예외를 던진다")
		void throwsSoldOutWhenSoldOut() {
			// given
			LimitedDrop drop = LimitedDropFixture.open(product, 10);
			drop.markSoldOut();

			// when & then
			assertThatThrownBy(() -> drop.validatePurchasable(drop.getOpenAt().plusMinutes(1)))
					.isInstanceOf(BusinessException.class)
					.extracting("errorCode")
					.isEqualTo(ErrorCode.LIMITED_SOLD_OUT);
		}

		@Test
		@DisplayName("현재 시각이 오픈 시각과 같으면 통과한다")
		void passesWhenNowEqualsOpenAt() {
			// given
			LimitedDrop drop = LimitedDropFixture.open(product, 10);

			// when & then
			assertThatCode(() -> drop.validatePurchasable(drop.getOpenAt()))
					.doesNotThrowAnyException();
		}

		@Test
		@DisplayName("OPEN 상태이고 오픈·마감 사이면 통과한다")
		void passesWhenOpenAndWithinPeriod() {
			// given
			LimitedDrop drop = LimitedDropFixture.open(product, 10);

			// when & then
			assertThatCode(() -> drop.validatePurchasable(drop.getOpenAt().plusMinutes(1)))
					.doesNotThrowAnyException();
		}
	}

	@Nested
	@DisplayName("recordSale()")
	class RecordSale {

		@Test
		@DisplayName("판매를 기록하면 판매 수량이 증가한다")
		void increasesSoldCount() {
			// given
			LimitedDrop drop = LimitedDropFixture.open(product, 10);

			// when
			drop.recordSale(3);

			// then
			assertThat(drop.getSoldCount()).isEqualTo(3);
		}

		@Test
		@DisplayName("마지막 남은 수량까지 판매하면 SOLD_OUT 으로 자동 전이한다")
		void transitionsToSoldOutWhenReachedTotalQuantity() {
			// given
			LimitedDrop drop = LimitedDropFixture.open(product, 5);

			// when
			drop.recordSale(5);

			// then
			assertThat(drop.getSoldCount()).isEqualTo(5);
			assertThat(drop.getStatus()).isEqualTo(LimitedDropStatus.SOLD_OUT);
		}

		@Test
		@DisplayName("총 수량을 초과하면 LIMITED_SOLD_OUT 예외를 던진다")
		void throwsWhenExceedsTotalQuantity() {
			// given
			LimitedDrop drop = LimitedDropFixture.open(product, 5);

			// when & then
			assertThatThrownBy(() -> drop.recordSale(6))
					.isInstanceOf(BusinessException.class)
					.extracting("errorCode")
					.isEqualTo(ErrorCode.LIMITED_SOLD_OUT);
		}

		@Test
		@DisplayName("수량이 0 이하면 COMMON_INVALID_INPUT 예외를 던진다")
		void throwsWhenQuantityNotPositive() {
			// given
			LimitedDrop drop = LimitedDropFixture.open(product, 5);

			// when & then
			assertThatThrownBy(() -> drop.recordSale(0))
					.isInstanceOf(BusinessException.class)
					.extracting("errorCode")
					.isEqualTo(ErrorCode.COMMON_INVALID_INPUT);
		}
	}

	@Nested
	@DisplayName("restoreSale()")
	class RestoreSale {

		@Test
		@DisplayName("복구하면 판매 수량이 감소한다")
		void decreasesSoldCount() {
			// given
			LimitedDrop drop = LimitedDropFixture.open(product, 10);
			drop.recordSale(3);

			// when
			drop.restoreSale(1);

			// then
			assertThat(drop.getSoldCount()).isEqualTo(2);
		}

		@Test
		@DisplayName("SOLD_OUT 상태에서 복구하면 OPEN 으로 되돌아간다")
		void revertsToOpenWhenSoldOut() {
			// given
			LimitedDrop drop = LimitedDropFixture.open(product, 5);
			drop.recordSale(5);

			// when
			drop.restoreSale(1);

			// then
			assertThat(drop.getStatus()).isEqualTo(LimitedDropStatus.OPEN);
			assertThat(drop.getSoldCount()).isEqualTo(4);
		}

		@Test
		@DisplayName("복구 수량이 판매 수량보다 크면 COMMON_INVALID_INPUT 예외를 던진다")
		void throwsWhenQuantityExceedsSoldCount() {
			// given
			LimitedDrop drop = LimitedDropFixture.open(product, 10);
			drop.recordSale(2);

			// when & then
			assertThatThrownBy(() -> drop.restoreSale(3))
					.isInstanceOf(BusinessException.class)
					.extracting("errorCode")
					.isEqualTo(ErrorCode.COMMON_INVALID_INPUT);
		}
	}

	@Nested
	@DisplayName("remainingQuantity() / isActive()")
	class RemainingAndActive {

		@Test
		@DisplayName("잔여 수량은 총 수량에서 판매 수량을 뺀 값이다")
		void calculatesRemainingQuantity() {
			// given
			LimitedDrop drop = LimitedDropFixture.open(product, 10);
			drop.recordSale(3);

			// when & then
			assertThat(drop.remainingQuantity()).isEqualTo(7);
		}

		@Test
		@DisplayName("CLOSED 가 아니면 활성 상태다")
		void isActiveWhenNotClosed() {
			// given
			LimitedDrop drop = LimitedDropFixture.scheduled(product);

			// when & then
			assertThat(drop.isActive()).isTrue();
		}

		@Test
		@DisplayName("CLOSED 면 활성 상태가 아니다")
		void isNotActiveWhenClosed() {
			// given
			LimitedDrop drop = LimitedDropFixture.open(product, 10);
			drop.close();

			// when & then
			assertThat(drop.isActive()).isFalse();
		}
	}

	@Nested
	@DisplayName("reschedule()")
	class Reschedule {

		@Test
		@DisplayName("SCHEDULED 상태면 수량과 일정이 교체된다")
		void replacesFieldsWhenScheduled() {
			// given
			LimitedDrop drop = LimitedDropFixture.scheduled(product);
			LocalDateTime newOpenAt = LocalDateTime.now().plusDays(3);
			LocalDateTime newCloseAt = LocalDateTime.now().plusDays(4);

			// when
			drop.reschedule(200, 3, newOpenAt, newCloseAt);

			// then
			assertThat(drop.getTotalQuantity()).isEqualTo(200);
			assertThat(drop.getPerMemberLimit()).isEqualTo(3);
			assertThat(drop.getOpenAt()).isEqualTo(newOpenAt);
			assertThat(drop.getCloseAt()).isEqualTo(newCloseAt);
		}

		@ParameterizedTest
		@EnumSource(value = LimitedDropStatus.class, names = {"OPEN", "SOLD_OUT", "CLOSED"})
		@DisplayName("SCHEDULED 상태가 아니면 LIMITED_INVALID_STATUS 예외를 던진다")
		void throwsWhenNotScheduled(LimitedDropStatus status) {
			// given
			LimitedDrop drop = LimitedDropFixture.withStatus(LimitedDropFixture.scheduled(product), status);

			// when & then
			assertThatThrownBy(() -> drop.reschedule(200, 3, LocalDateTime.now().plusDays(3),
					LocalDateTime.now().plusDays(4)))
					.isInstanceOf(BusinessException.class)
					.extracting("errorCode")
					.isEqualTo(ErrorCode.LIMITED_INVALID_STATUS);
		}

		@Test
		@DisplayName("마감 시각이 오픈 시각보다 이후가 아니면 COMMON_INVALID_INPUT 예외를 던진다")
		void throwsWhenCloseAtBeforeOpenAt() {
			// given
			LimitedDrop drop = LimitedDropFixture.scheduled(product);
			LocalDateTime openAt = LocalDateTime.now().plusDays(4);
			LocalDateTime closeAt = LocalDateTime.now().plusDays(3);

			// when & then
			assertThatThrownBy(() -> drop.reschedule(200, 3, openAt, closeAt))
					.isInstanceOf(BusinessException.class)
					.extracting("errorCode")
					.isEqualTo(ErrorCode.COMMON_INVALID_INPUT);
		}

		@Test
		@DisplayName("회원당 구매 제한이 총 수량을 초과하면 COMMON_INVALID_INPUT 예외를 던진다")
		void throwsWhenPerMemberLimitExceedsTotalQuantity() {
			// given
			LimitedDrop drop = LimitedDropFixture.scheduled(product);

			// when & then
			assertThatThrownBy(() -> drop.reschedule(5, 6, LocalDateTime.now().plusDays(3),
					LocalDateTime.now().plusDays(4)))
					.isInstanceOf(BusinessException.class)
					.extracting("errorCode")
					.isEqualTo(ErrorCode.COMMON_INVALID_INPUT);
		}
	}
}
