package com.groove.member.mapper;

import java.time.LocalDateTime;
import java.util.List;

import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import com.groove.member.dto.AdminMemberActivitySummary;
import com.groove.member.dto.AdminMemberSearchCondition;
import com.groove.member.dto.AdminMemberSummaryResponse;

/** 관리자 회원 목록·활동 요약 조회 전용. */
@Mapper
public interface MemberQueryMapper {

	List<AdminMemberSummaryResponse> findAdminMembers(AdminMemberSearchCondition condition);

	long countAdminMembers(AdminMemberSearchCondition condition);

	// member 를 LEFT JOIN 기준으로 두고 조회하므로 대상 회원이 존재하면 항상 한 행을 반환한다.
	AdminMemberActivitySummary findActivitySummary(@Param("memberId") Long memberId, @Param("now") LocalDateTime now);
}
