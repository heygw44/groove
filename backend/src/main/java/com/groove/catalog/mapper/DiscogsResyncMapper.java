package com.groove.catalog.mapper;

import java.time.LocalDateTime;
import java.util.List;

import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import com.groove.catalog.dto.DiscogsResyncCandidate;

/** Discogs 재검증 후보 선정. 읽기 전용 복잡 쿼리라 MyBatis 로 뺐다. */
@Mapper
public interface DiscogsResyncMapper {

	/**
	 * stale 하고 discogs_release_id 를 가진 상품만 대상으로 한다. viewPriority 가 true 면 최근 7일
	 * 조회수·최근 조회 순으로, 아니면 오래 갱신되지 않은 순으로 정렬한다.
	 */
	List<DiscogsResyncCandidate> findCandidates(@Param("staleBefore") LocalDateTime staleBefore,
			@Param("viewSince") LocalDateTime viewSince, @Param("visibleOnly") boolean visibleOnly,
			@Param("viewPriority") boolean viewPriority, @Param("limit") int limit);

	/** 예산 경보 계산용. limit 없이 전체 후보 수를 센다. */
	long countStale(@Param("staleBefore") LocalDateTime staleBefore);
}
