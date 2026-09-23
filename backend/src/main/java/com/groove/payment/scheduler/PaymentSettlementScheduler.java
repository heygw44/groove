package com.groove.payment.scheduler;

import java.time.Clock;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import com.groove.global.alert.Alert;
import com.groove.global.alert.AlertNotifier;
import com.groove.global.lifecycle.ShutdownSignal;
import com.groove.payment.client.PaymentClient;
import com.groove.payment.client.dto.PaymentTransaction;
import com.groove.payment.config.PaymentSettlementProperties;
import com.groove.payment.service.PaymentSettlementLock;
import com.groove.payment.service.PaymentSettlementReport;
import com.groove.payment.service.PaymentSettlementService;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

/**
 * 하루치(어제) 결제를 토스 거래 조회로 대조한다. 04:00 DB 백업 뒤인 05:10 에 돈다. overlap 은 자정 경계에서
 * 생기는 시각 오차를 흡수하기 위한 여유고, 실제 대상 구간(from~to)은 그대로 {@link PaymentSettlementService}
 * 에 넘긴다.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class PaymentSettlementScheduler {

	private final PaymentSettlementService settlementService;
	private final PaymentSettlementLock settlementLock;
	private final PaymentClient paymentClient;
	private final PaymentSettlementProperties settlementProperties;
	private final ShutdownSignal shutdownSignal;
	private final Clock clock;
	private final AlertNotifier alertNotifier;

	@Scheduled(cron = "${groove.payment.settlement.cron}", zone = "Asia/Seoul")
	public void settle() {
		if (shutdownSignal.isShuttingDown()) {
			return;
		}
		boolean acquired = settlementLock.runExclusively(this::runSettlement);
		if (!acquired) {
			log.info("결제 정산 대사 락 획득 실패로 건너뛴다");
		}
	}

	private void runSettlement() {
		LocalDate today = LocalDate.now(clock);
		LocalDateTime to = today.atStartOfDay();
		LocalDateTime from = to.minusDays(1);
		List<PaymentTransaction> transactions;
		try {
			transactions = paymentClient.listTransactions(from.minus(settlementProperties.overlap()),
					to.plus(settlementProperties.overlap()));
		} catch (RuntimeException ex) {
			log.error("결제 정산 거래 조회 실패 from={} to={}", from, to, ex);
			alertNotifier.notify(Alert.critical("payment.settlement-failed",
					"결제 정산 거래 조회 실패 from=" + from + ", to=" + to + ", message=" + ex.getMessage(), null));
			return;
		}
		PaymentSettlementReport report = settlementService.reconcile(transactions, from, to);
		log.info("결제 정산 대사 완료 from={} to={} report={}", from, to, report);
	}
}
