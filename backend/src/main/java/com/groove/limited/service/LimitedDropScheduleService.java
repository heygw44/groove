package com.groove.limited.service;

import java.time.LocalDateTime;
import java.util.Optional;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.groove.limited.entity.LimitedDrop;
import com.groove.limited.entity.LimitedDropStatus;
import com.groove.limited.repository.LimitedDropRepository;

import lombok.RequiredArgsConstructor;

/** 스케줄러가 드롭 하나를 열거나 닫는 트랜잭션 단위. 관리자 수동 오픈/마감과 같은 순서(상태 전이 -> Redis)를 따른다. */
@Service
@RequiredArgsConstructor
public class LimitedDropScheduleService {

	private final LimitedDropRepository limitedDropRepository;
	private final LimitedDropRedisService limitedDropRedisService;

	@Transactional
	public boolean open(Long dropId, LocalDateTime now) {
		Optional<LimitedDrop> found = limitedDropRepository.findByIdForUpdate(dropId);
		// 락을 잡은 뒤 다시 확인해야 관리자가 먼저 연 드롭을 건너뛴다.
		if (found.isEmpty() || found.get().getStatus() != LimitedDropStatus.SCHEDULED
				|| now.isBefore(found.get().getOpenAt())) {
			return false;
		}
		LimitedDrop drop = found.get();
		drop.open();
		// 상태 전이 뒤에 Redis 를 치므로 실패 시 롤백되고 다음 주기에 다시 시도한다.
		limitedDropRedisService.initStock(drop.getId(), drop.remainingQuantity());
		return true;
	}

	@Transactional
	public boolean close(Long dropId, LocalDateTime now) {
		Optional<LimitedDrop> found = limitedDropRepository.findByIdForUpdate(dropId);
		if (found.isEmpty() || !isClosable(found.get().getStatus()) || now.isBefore(found.get().getCloseAt())) {
			return false;
		}
		LimitedDrop drop = found.get();
		drop.close();
		limitedDropRedisService.clear(drop.getId());
		return true;
	}

	private boolean isClosable(LimitedDropStatus status) {
		return status == LimitedDropStatus.OPEN || status == LimitedDropStatus.SOLD_OUT;
	}
}
