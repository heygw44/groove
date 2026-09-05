package com.groove.member.dto;

import com.groove.member.entity.MemberRole;
import com.groove.member.entity.MemberStatus;

/** {@link com.groove.member.mapper.MemberQueryMapper} 의 관리자 회원 목록 조회 조건. */
public record AdminMemberSearchCondition(
		String keyword,
		MemberStatus status,
		MemberRole role,
		int page,
		int size
) {

	public int offset() {
		return page * size;
	}
}
