package com.groove.limited.scheduler;

import static com.groove.limited.entity.LimitedDropStatus.OPEN;
import static com.groove.limited.entity.LimitedDropStatus.SCHEDULED;
import static com.groove.limited.entity.LimitedDropStatus.SOLD_OUT;

import java.time.Clock;
import java.time.LocalDateTime;
import java.util.List;
import java.util.function.BiPredicate;

import org.springframework.dao.DataAccessException;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import com.groove.limited.entity.LimitedDrop;
import com.groove.limited.repository.LimitedDropRepository;
import com.groove.limited.service.LimitedDropRedisService;
import com.groove.limited.service.LimitedDropScheduleService;
import com.groove.limited.service.LimitedDropSyncService;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

/** 한정반 드롭을 정시에 열고 닫는다. 드롭마다 서비스 트랜잭션을 따로 타서 한 건 실패가 나머지를 막지 않는다. */
@Component
@RequiredArgsConstructor
@Slf4j
public class LimitedDropScheduler {

	private final LimitedDropRepository limitedDropRepository;
	private final LimitedDropScheduleService scheduleService;
	private final LimitedDropRedisService limitedDropRedisService;
	private final LimitedDropSyncService limitedDropSyncService;
	private final Clock clock;

	@Scheduled(fixedDelay = 10_000, initialDelay = 10_000)
	public void run() {
		LocalDateTime now = LocalDateTime.now(clock);
		int opened = process(limitedDropRepository.findAllByStatusAndOpenAtLessThanEqual(SCHEDULED, now), now,
				scheduleService::open, "오픈");
		// 오픈과 마감 시각이 모두 지난 드롭은 위에서 열린 직후 여기서 닫힌다.
		int closed = process(limitedDropRepository.findAllByStatusInAndCloseAtLessThanEqual(List.of(OPEN, SOLD_OUT),
				now), now, scheduleService::close, "마감");
		if (opened > 0 || closed > 0) {
			log.info("한정반 드롭 상태 전이 완료 opened={} closed={}", opened, closed);
		}
		restoreMissingKeys();
	}

	/** 오픈·마감 처리 뒤 OPEN/SOLD_OUT 드롭 중 Redis 재고 키가 없는 것을 DB 기준으로 재적재한다. */
	private void restoreMissingKeys() {
		List<Long> ids = limitedDropRepository.findIdsByStatusIn(List.of(OPEN, SOLD_OUT));
		if (ids.isEmpty()) {
			return;
		}
		List<Long> missing;
		try {
			missing = limitedDropRedisService.findMissingStock(ids);
		} catch (DataAccessException e) {
			log.warn("한정반 재고 키 점검 실패(Redis)", e);
			return;
		}
		for (Long dropId : missing) {
			try {
				limitedDropSyncService.sync(dropId);
				log.info("한정반 재고 키 유실 복구 dropId={}", dropId);
			} catch (RuntimeException e) {
				log.warn("한정반 재고 키 유실 복구 실패 dropId={}", dropId, e);
			}
		}
	}

	private int process(List<LimitedDrop> drops, LocalDateTime now, BiPredicate<Long, LocalDateTime> action,
			String label) {
		int count = 0;
		for (LimitedDrop drop : drops) {
			Long dropId = drop.getId();
			try {
				if (action.test(dropId, now)) {
					count++;
				}
			} catch (RuntimeException e) {
				log.warn("한정반 드롭 {} 처리 실패 dropId={}", label, dropId, e);
			}
		}
		return count;
	}
}
