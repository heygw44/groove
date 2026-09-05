package com.groove.member.service;

import java.time.Clock;
import java.time.LocalDateTime;
import java.util.List;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.groove.admin.entity.AdminAuditAction;
import com.groove.admin.entity.AdminAuditTargetType;
import com.groove.admin.service.AdminAuditLogService;
import com.groove.auth.repository.RefreshTokenRepository;
import com.groove.global.common.BusinessException;
import com.groove.global.common.ErrorCode;
import com.groove.global.common.PageResponse;
import com.groove.member.dto.AdminMemberActivitySummary;
import com.groove.member.dto.AdminMemberDetailResponse;
import com.groove.member.dto.AdminMemberSearchCondition;
import com.groove.member.dto.AdminMemberSearchRequest;
import com.groove.member.dto.AdminMemberStatusChangeRequest;
import com.groove.member.dto.AdminMemberSummaryResponse;
import com.groove.member.entity.Member;
import com.groove.member.entity.MemberRole;
import com.groove.member.entity.MemberStatus;
import com.groove.member.mapper.MemberQueryMapper;
import com.groove.member.repository.MemberRepository;
import com.groove.order.dto.OrderSearchCondition;
import com.groove.order.dto.OrderSummaryResponse;
import com.groove.order.mapper.OrderQueryMapper;

import lombok.RequiredArgsConstructor;

/** 관리자 회원 목록·상세 조회, 상태(정지/활성) 전이를 담당한다. */
@Service
@Transactional(readOnly = true)
@RequiredArgsConstructor
public class AdminMemberService {

	private static final int RECENT_ORDER_LIMIT = 5;

	private final MemberRepository memberRepository;
	private final MemberQueryMapper memberQueryMapper;
	private final OrderQueryMapper orderQueryMapper;
	private final RefreshTokenRepository refreshTokenRepository;
	private final AdminAuditLogService adminAuditLogService;
	private final Clock clock;

	public PageResponse<AdminMemberSummaryResponse> getList(AdminMemberSearchRequest request) {
		AdminMemberSearchCondition condition = request.toCondition();
		long totalElements = memberQueryMapper.countAdminMembers(condition);
		if (totalElements == 0) {
			return PageResponse.of(List.of(), condition.page(), condition.size(), 0);
		}
		List<AdminMemberSummaryResponse> content = memberQueryMapper.findAdminMembers(condition);
		return PageResponse.of(content, condition.page(), condition.size(), totalElements);
	}

	public AdminMemberDetailResponse getDetail(Long memberId) {
		Member member = memberRepository.findById(memberId)
				.orElseThrow(() -> new BusinessException(ErrorCode.MEMBER_NOT_FOUND));
		return toDetailResponse(member);
	}

	@Transactional
	public AdminMemberDetailResponse changeStatus(Long adminId, Long memberId, AdminMemberStatusChangeRequest request) {
		if (adminId.equals(memberId)) {
			throw new BusinessException(ErrorCode.ADMIN_CANNOT_MODIFY_SELF);
		}
		Member member = memberRepository.findById(memberId)
				.orElseThrow(() -> new BusinessException(ErrorCode.MEMBER_NOT_FOUND));
		if (member.getRole() == MemberRole.ADMIN) {
			throw new BusinessException(ErrorCode.ADMIN_CANNOT_MODIFY_ADMIN);
		}

		MemberStatus previous = member.getStatus();
		MemberStatus next = request.status();
		if (previous == next) {
			return toDetailResponse(member);
		}

		if (next == MemberStatus.SUSPENDED) {
			member.suspend();
			refreshTokenRepository.deleteByMemberId(memberId);
		} else {
			member.activate();
		}

		adminAuditLogService.record(adminId, AdminAuditAction.MEMBER_STATUS_CHANGE, AdminAuditTargetType.MEMBER,
				memberId, previous.name() + "->" + next.name());
		return toDetailResponse(member);
	}

	private AdminMemberDetailResponse toDetailResponse(Member member) {
		AdminMemberActivitySummary activitySummary =
				memberQueryMapper.findActivitySummary(member.getId(), LocalDateTime.now(clock));
		List<OrderSummaryResponse> recentOrders = orderQueryMapper.findMyOrders(
				new OrderSearchCondition(member.getId(), null, 0, RECENT_ORDER_LIMIT));
		return AdminMemberDetailResponse.of(member, activitySummary, recentOrders);
	}
}
