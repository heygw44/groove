package com.groove.recommend.service;

import java.time.LocalDateTime;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.groove.recommend.repository.ProductViewLogRepository;

import lombok.RequiredArgsConstructor;

/** 상품 조회 로그 보관 기간 정책. 단일 대량 DELETE 는 InnoDB 갭락과 긴 트랜잭션을 만들어 배치로 나눠 지운다. */
@Service
@Transactional(readOnly = true)
@RequiredArgsConstructor
public class ProductViewLogCleanupService {

	private final ProductViewLogRepository productViewLogRepository;

	@Transactional
	public int deleteBatch(LocalDateTime threshold, int size) {
		return productViewLogRepository.deleteExpired(threshold, size);
	}
}
