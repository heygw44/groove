package com.groove.stats.service;

import java.time.Clock;
import java.time.LocalDate;

import org.springframework.stereotype.Service;

import com.groove.admin.dto.StatsPeriod;
import com.groove.admin.entity.AdminAuditAction;
import com.groove.admin.entity.AdminAuditTargetType;
import com.groove.admin.service.AdminAuditLogService;
import com.groove.global.common.BusinessException;
import com.groove.global.common.ErrorCode;
import com.groove.stats.dto.SalesAggregationRequest;
import com.groove.stats.dto.SalesAggregationResponse;

import lombok.RequiredArgsConstructor;

/**
 * 관리자 수동 재집계. 운영 백필의 유일한 경로다. 야간/증분 스케줄러와 같은 {@link AggregationLock} 으로 감싸
 * 동시 실행을 막는다. 스케줄러는 락 미획득을 조용히 넘기지만, 이 API 는 요청자에게 409 로 알려야 한다.
 *
 * <p>클래스에 트랜잭션을 걸지 않는다. 날짜마다 {@link SalesAggregationService#aggregateDate} 가 자기 트랜잭션을
 * 새로 열어야 롱 트랜잭션이 생기지 않는데, 이 메서드에 트랜잭션을 걸면 그 호출들이 전부 이 트랜잭션에 합류한다.</p>
 */
@Service
@RequiredArgsConstructor
public class SalesAggregationAdminService {

	private final SalesAggregationService salesAggregationService;
	private final AggregationLock aggregationLock;
	private final AdminAuditLogService adminAuditLogService;
	private final Clock clock;

	public SalesAggregationResponse aggregate(Long adminId, SalesAggregationRequest request) {
		StatsPeriod period = StatsPeriod.resolve(request.from(), request.to(), LocalDate.now(clock));

		int[] aggregatedDays = new int[1];
		boolean acquired = aggregationLock.runExclusively(() -> {
			for (LocalDate date : period.dates()) {
				salesAggregationService.aggregateDate(date);
				aggregatedDays[0]++;
			}
		});
		if (!acquired) {
			throw new BusinessException(ErrorCode.STATS_AGGREGATION_RUNNING);
		}

		adminAuditLogService.record(adminId, AdminAuditAction.SALES_AGGREGATION_RUN,
				AdminAuditTargetType.SALES_AGGREGATION, null,
				"from=" + period.from() + ",to=" + period.to());

		return new SalesAggregationResponse(aggregatedDays[0]);
	}
}
