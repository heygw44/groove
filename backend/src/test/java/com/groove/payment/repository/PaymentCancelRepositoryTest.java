package com.groove.payment.repository;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.tuple;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.Limit;

import com.groove.fixture.MemberFixture;
import com.groove.fixture.OrderFixture;
import com.groove.fixture.PaymentFixture;
import com.groove.member.entity.Member;
import com.groove.member.repository.MemberRepository;
import com.groove.order.entity.Order;
import com.groove.order.repository.OrderRepository;
import com.groove.payment.dto.PaymentCancelRetryCandidate;
import com.groove.payment.entity.Payment;
import com.groove.payment.entity.PaymentCancel;
import com.groove.payment.entity.PaymentStatus;
import com.groove.support.DataJpaTestSupport;

class PaymentCancelRepositoryTest extends DataJpaTestSupport {

	@Autowired
	private PaymentCancelRepository paymentCancelRepository;

	@Autowired
	private PaymentRepository paymentRepository;

	@Autowired
	private OrderRepository orderRepository;

	@Autowired
	private MemberRepository memberRepository;

	@Nested
	@DisplayName("findRetryCandidates()")
	class FindRetryCandidates {

		private static final LocalDateTime INITIAL_CURSOR = LocalDateTime.of(1970, 1, 1, 0, 0);

		@Test
		@DisplayName("결제가 DONE·PARTIAL_CANCELED 이고 retryBefore 이전에 요청된 REQUESTED 행만 포함한다")
		void includesOnlyStaleRequestedRowsOfRefundablePayments() {
			// given
			LocalDateTime retryBefore = LocalDateTime.of(2034, 1, 1, 0, 0);
			Payment done = savePayment("filter-done", PaymentStatus.DONE);
			Payment partialCanceled = savePayment("filter-partial", PaymentStatus.PARTIAL_CANCELED);
			Payment cancelRequested = savePayment("filter-cancelreq", PaymentStatus.CANCEL_REQUESTED);
			Long doneRowId = saveRequested(done, "filter-done-1", retryBefore.minusMinutes(3)).getId();
			Long partialRowId = saveRequested(partialCanceled, "filter-partial-1", retryBefore.minusMinutes(2))
					.getId();
			Long boundaryRowId = saveRequested(done, "filter-done-2", retryBefore).getId();
			Long cancelRequestedRowId = saveRequested(cancelRequested, "filter-cancelreq-1",
					retryBefore.minusMinutes(3)).getId();
			Long tooRecentRowId = saveRequested(done, "filter-done-3", retryBefore.plusMinutes(1)).getId();
			PaymentCancel completed = saveRequested(done, "filter-done-4", retryBefore.minusMinutes(4));
			completed.complete("txn-filter-done-4", retryBefore.minusMinutes(4));
			paymentCancelRepository.saveAndFlush(completed);
			List<Long> ids = List.of(doneRowId, partialRowId, boundaryRowId, cancelRequestedRowId, tooRecentRowId,
					completed.getId());

			// when
			List<PaymentCancelRetryCandidate> result = paymentCancelRepository.findRetryCandidates(retryBefore,
					INITIAL_CURSOR, 0L, Limit.of(100));

			// then
			assertThat(result).filteredOn(candidate -> ids.contains(candidate.paymentCancelId()))
					.extracting(PaymentCancelRetryCandidate::paymentCancelId)
					.containsExactly(doneRowId, partialRowId, boundaryRowId);
		}

		@Test
		@DisplayName("커서 뒤의 후보만 요청 시각, id 순으로 돌려주고 같은 시각이면 커서 id 보다 큰 행만 포함한다")
		void returnsOnlyCandidatesAfterCursorInOrder() {
			// given
			LocalDateTime retryBefore = LocalDateTime.of(2034, 2, 1, 0, 0);
			LocalDateTime early = retryBefore.minusDays(3);
			LocalDateTime tie = retryBefore.minusDays(2);
			LocalDateTime late = retryBefore.minusDays(1);
			Payment payment = savePayment("cursor", PaymentStatus.PARTIAL_CANCELED);
			// 저장 순서상 id 는 late < tieFirst < tieSecond < early 지만 정렬은 요청 시각이 우선이다.
			Long lateId = saveRequested(payment, "cursor-late", late).getId();
			Long tieFirstId = saveRequested(payment, "cursor-tie-1", tie).getId();
			Long tieSecondId = saveRequested(payment, "cursor-tie-2", tie).getId();
			Long earlyId = saveRequested(payment, "cursor-early", early).getId();
			List<Long> ids = List.of(lateId, tieFirstId, tieSecondId, earlyId);

			// when
			List<PaymentCancelRetryCandidate> result = paymentCancelRepository.findRetryCandidates(retryBefore, tie,
					tieFirstId, Limit.of(100));

			// then
			assertThat(result).filteredOn(candidate -> ids.contains(candidate.paymentCancelId()))
					.extracting(PaymentCancelRetryCandidate::paymentCancelId,
							PaymentCancelRetryCandidate::requestedAt)
					.containsExactly(tuple(tieSecondId, tie), tuple(lateId, late));
		}

		@Test
		@DisplayName("커서보다 앞선 행이 없으면 요청 시각, id 순으로 limit 만큼만 돌려준다")
		void returnsPageInOrderWithinLimit() {
			// given
			LocalDateTime retryBefore = LocalDateTime.of(2034, 3, 1, 0, 0);
			LocalDateTime tie = retryBefore.minusDays(2);
			LocalDateTime late = retryBefore.minusDays(1);
			Payment payment = savePayment("page", PaymentStatus.DONE);
			saveRequested(payment, "page-late", late);
			Long tieFirstId = saveRequested(payment, "page-tie-1", tie).getId();
			Long tieSecondId = saveRequested(payment, "page-tie-2", tie).getId();

			// when
			List<PaymentCancelRetryCandidate> result = paymentCancelRepository.findRetryCandidates(retryBefore,
					tie.minusSeconds(1), 0L, Limit.of(2));

			// then
			assertThat(result).extracting(PaymentCancelRetryCandidate::paymentCancelId)
					.containsExactly(tieFirstId, tieSecondId);
		}

		private Payment savePayment(String key, PaymentStatus status) {
			Member member = memberRepository.save(MemberFixture.create("payment-cancel-repo-" + key + "@groove.com"));
			Order order = orderRepository.save(OrderFixture.create(member, "20340101-PCR-" + key));
			Payment payment = PaymentFixture.approved(order, "tviva-pcr-" + key);
			return paymentRepository.save(PaymentFixture.withStatus(payment, status));
		}

		private PaymentCancel saveRequested(Payment payment, String key, LocalDateTime requestedAt) {
			return paymentCancelRepository.saveAndFlush(PaymentCancel.request(payment, "cancel-pcr-" + key,
					BigDecimal.ONE, "부분 반품", requestedAt));
		}
	}
}
