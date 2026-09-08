package com.groove.stats.service;

import org.springframework.data.domain.Page;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.groove.global.common.PageResponse;
import com.groove.stats.dto.ReconcileLogResponse;
import com.groove.stats.dto.ReconcileLogSearchRequest;
import com.groove.stats.entity.SalesReconcileLog;
import com.groove.stats.repository.SalesReconcileLogRepository;

import lombok.RequiredArgsConstructor;

/** 대사 로그 목록 조회. */
@Service
@Transactional(readOnly = true)
@RequiredArgsConstructor
public class SalesReconcileLogQueryService {

	private final SalesReconcileLogRepository salesReconcileLogRepository;

	public PageResponse<ReconcileLogResponse> getList(ReconcileLogSearchRequest request) {
		Page<SalesReconcileLog> page = request.repaired() == null
				? salesReconcileLogRepository.findAll(request.toPageable())
				: salesReconcileLogRepository.findAllByRepaired(request.repaired(), request.toPageable());
		return PageResponse.from(page.map(ReconcileLogResponse::from));
	}
}
