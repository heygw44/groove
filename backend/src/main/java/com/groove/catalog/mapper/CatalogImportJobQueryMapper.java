package com.groove.catalog.mapper;

import java.util.List;

import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import com.groove.catalog.dto.CatalogImportJobHistoryRow;

/**
 * 카탈로그 적재 잡 이력 조회 전용. {@code JobExplorer} 로 인스턴스마다 실행 이력을 반복 조회하던 N+1 을
 * 인스턴스 id 목록 IN 절 한 번으로 대체한다. 잡 실행에 쓰지 않는 실행 컨텍스트(BATCH_*_EXECUTION_CONTEXT)는 조회하지 않는다.
 */
@Mapper
public interface CatalogImportJobQueryMapper {

	List<CatalogImportJobHistoryRow> findLatestExecutions(@Param("jobInstanceIds") List<Long> jobInstanceIds,
			@Param("masterIdParameterName") String masterIdParameterName);
}
