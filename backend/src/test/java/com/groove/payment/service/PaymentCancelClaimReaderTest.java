package com.groove.payment.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.verifyNoInteractions;

import java.util.List;
import java.util.Set;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import com.groove.payment.entity.PaymentCancelStatus;
import com.groove.payment.repository.PaymentCancelRepository;

@ExtendWith(MockitoExtension.class)
class PaymentCancelClaimReaderTest {

	private static final Long CLAIM_ID = 500L;

	@Mock
	PaymentCancelRepository paymentCancelRepository;

	@InjectMocks
	PaymentCancelClaimReader reader;

	@Nested
	@DisplayName("hasPendingRefund()")
	class HasPendingRefund {

		@Test
		@DisplayName("REQUESTED 환불 행이 있으면 true 를 반환한다")
		void returnsTrueWhenRequestedRowExists() {
			// given
			given(paymentCancelRepository.existsByOrderClaimIdAndStatusIn(CLAIM_ID,
					Set.of(PaymentCancelStatus.REQUESTED))).willReturn(true);

			// when & then
			assertThat(reader.hasPendingRefund(CLAIM_ID)).isTrue();
		}

		@Test
		@DisplayName("REQUESTED 환불 행이 없으면 false 를 반환한다")
		void returnsFalseWhenNoRequestedRow() {
			// given
			given(paymentCancelRepository.existsByOrderClaimIdAndStatusIn(CLAIM_ID,
					Set.of(PaymentCancelStatus.REQUESTED))).willReturn(false);

			// when & then
			assertThat(reader.hasPendingRefund(CLAIM_ID)).isFalse();
		}
	}

	@Nested
	@DisplayName("hasAnyRefund()")
	class HasAnyRefund {

		@Test
		@DisplayName("상태와 무관하게 환불 행이 있으면 true 를 반환한다")
		void returnsTrueWhenAnyRowExists() {
			// given
			given(paymentCancelRepository.existsByOrderClaimId(CLAIM_ID)).willReturn(true);

			// when & then
			assertThat(reader.hasAnyRefund(CLAIM_ID)).isTrue();
		}

		@Test
		@DisplayName("환불 행이 없으면 false 를 반환한다")
		void returnsFalseWhenNoRow() {
			// given
			given(paymentCancelRepository.existsByOrderClaimId(CLAIM_ID)).willReturn(false);

			// when & then
			assertThat(reader.hasAnyRefund(CLAIM_ID)).isFalse();
		}
	}

	@Nested
	@DisplayName("findPendingRefundOrderItemIds()")
	class FindPendingRefundOrderItemIds {

		@Test
		@DisplayName("조회 대상이 비어 있으면 저장소를 호출하지 않고 빈 집합을 반환한다")
		void returnsEmptyWithoutQueryWhenInputEmpty() {
			// when
			Set<Long> result = reader.findPendingRefundOrderItemIds(List.of());

			// then
			assertThat(result).isEmpty();
			verifyNoInteractions(paymentCancelRepository);
		}

		@Test
		@DisplayName("결과를 기다리는 환불이 걸린 상품주문 id 를 집합으로 반환한다")
		void returnsPendingItemIdsAsSet() {
			// given
			given(paymentCancelRepository.findPendingRefundOrderItemIds(List.of(1L, 2L))).willReturn(List.of(2L));

			// when
			Set<Long> result = reader.findPendingRefundOrderItemIds(List.of(1L, 2L));

			// then
			assertThat(result).containsExactly(2L);
		}
	}
}
