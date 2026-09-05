package com.groove.member.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.List;
import java.util.Optional;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import com.groove.admin.entity.AdminAuditAction;
import com.groove.admin.entity.AdminAuditTargetType;
import com.groove.admin.service.AdminAuditLogService;
import com.groove.auth.repository.RefreshTokenRepository;
import com.groove.fixture.MemberFixture;
import com.groove.global.common.BusinessException;
import com.groove.global.common.ErrorCode;
import com.groove.global.common.PageResponse;
import com.groove.member.dto.AdminMemberActivitySummary;
import com.groove.member.dto.AdminMemberDetailResponse;
import com.groove.member.dto.AdminMemberSearchRequest;
import com.groove.member.dto.AdminMemberStatusChangeRequest;
import com.groove.member.dto.AdminMemberSummaryResponse;
import com.groove.member.entity.Member;
import com.groove.member.entity.MemberRole;
import com.groove.member.entity.MemberStatus;
import com.groove.member.mapper.MemberQueryMapper;
import com.groove.member.repository.MemberRepository;
import com.groove.order.mapper.OrderQueryMapper;

@ExtendWith(MockitoExtension.class)
class AdminMemberServiceTest {

	private static final Long ADMIN_ID = 1L;
	private static final Long MEMBER_ID = 2L;

	@Mock
	MemberRepository memberRepository;

	@Mock
	MemberQueryMapper memberQueryMapper;

	@Mock
	OrderQueryMapper orderQueryMapper;

	@Mock
	RefreshTokenRepository refreshTokenRepository;

	@Mock
	AdminAuditLogService adminAuditLogService;

	AdminMemberService adminMemberService;

	LocalDateTime now;

	@BeforeEach
	void setUp() {
		Clock clock = Clock.fixed(Instant.parse("2026-09-04T03:00:00Z"), ZoneId.of("Asia/Seoul"));
		now = LocalDateTime.now(clock);
		adminMemberService = new AdminMemberService(memberRepository, memberQueryMapper, orderQueryMapper,
				refreshTokenRepository, adminAuditLogService, clock);
	}

	@Nested
	@DisplayName("getList()")
	class GetList {

		@Test
		@DisplayName("조건에 맞는 회원이 없으면 매퍼를 호출하지 않고 빈 페이지를 반환한다")
		void returnsEmptyPageWhenNoMembers() {
			// given
			given(memberQueryMapper.countAdminMembers(any())).willReturn(0L);
			AdminMemberSearchRequest request = new AdminMemberSearchRequest(null, null, null, null, null);

			// when
			PageResponse<AdminMemberSummaryResponse> response = adminMemberService.getList(request);

			// then
			assertThat(response.content()).isEmpty();
			assertThat(response.totalElements()).isZero();
			verify(memberQueryMapper, never()).findAdminMembers(any());
		}

		@Test
		@DisplayName("조건에 맞는 회원이 있으면 목록과 총 개수를 반환한다")
		void returnsMembersWhenPresent() {
			// given
			AdminMemberSummaryResponse summary = new AdminMemberSummaryResponse(MEMBER_ID, "groover@groove.com",
					"그루버", MemberRole.USER, MemberStatus.ACTIVE, 3L, new BigDecimal("90000"), now);
			given(memberQueryMapper.countAdminMembers(any())).willReturn(1L);
			given(memberQueryMapper.findAdminMembers(any())).willReturn(List.of(summary));
			AdminMemberSearchRequest request = new AdminMemberSearchRequest("groove", null, null, null, null);

			// when
			PageResponse<AdminMemberSummaryResponse> response = adminMemberService.getList(request);

			// then
			assertThat(response.content()).containsExactly(summary);
			assertThat(response.totalElements()).isEqualTo(1);
		}
	}

	@Nested
	@DisplayName("getDetail()")
	class GetDetail {

		@Test
		@DisplayName("존재하는 회원이면 활동 요약과 최근 주문을 포함한 상세를 반환한다")
		void returnsDetailWithActivitySummaryAndRecentOrders() {
			// given
			Member member = MemberFixture.withId(MemberFixture.create(), MEMBER_ID);
			AdminMemberActivitySummary activitySummary =
					new AdminMemberActivitySummary(3L, new BigDecimal("90000"), 1L);
			given(memberRepository.findById(MEMBER_ID)).willReturn(Optional.of(member));
			given(memberQueryMapper.findActivitySummary(eq(MEMBER_ID), any())).willReturn(activitySummary);
			given(orderQueryMapper.findMyOrders(any())).willReturn(List.of());

			// when
			AdminMemberDetailResponse response = adminMemberService.getDetail(MEMBER_ID);

			// then
			assertThat(response.id()).isEqualTo(MEMBER_ID);
			assertThat(response.orderCount()).isEqualTo(3L);
			assertThat(response.totalPaymentAmount()).isEqualByComparingTo("90000");
			assertThat(response.usableCouponCount()).isEqualTo(1L);
			assertThat(response.recentOrders()).isEmpty();
		}

		@Test
		@DisplayName("존재하지 않는 회원이면 MEMBER_NOT_FOUND 예외를 던진다")
		void throwsWhenMemberNotFound() {
			// given
			given(memberRepository.findById(MEMBER_ID)).willReturn(Optional.empty());

			// when & then
			assertThatThrownBy(() -> adminMemberService.getDetail(MEMBER_ID))
					.isInstanceOf(BusinessException.class)
					.extracting("errorCode")
					.isEqualTo(ErrorCode.MEMBER_NOT_FOUND);
		}
	}

	@Nested
	@DisplayName("changeStatus()")
	class ChangeStatus {

		@Test
		@DisplayName("자기 자신을 대상으로 하면 ADMIN_CANNOT_MODIFY_SELF 예외를 던지고 조회조차 하지 않는다")
		void throwsWhenTargetIsSelf() {
			// given
			AdminMemberStatusChangeRequest request = new AdminMemberStatusChangeRequest(MemberStatus.SUSPENDED);

			// when & then
			assertThatThrownBy(() -> adminMemberService.changeStatus(ADMIN_ID, ADMIN_ID, request))
					.isInstanceOf(BusinessException.class)
					.extracting("errorCode")
					.isEqualTo(ErrorCode.ADMIN_CANNOT_MODIFY_SELF);
			verify(memberRepository, never()).findById(any());
		}

		@Test
		@DisplayName("대상이 다른 관리자면 ADMIN_CANNOT_MODIFY_ADMIN 예외를 던진다")
		void throwsWhenTargetIsAdmin() {
			// given
			Member otherAdmin = MemberFixture.withId(MemberFixture.createAdmin("other-admin@groove.com"), MEMBER_ID);
			given(memberRepository.findById(MEMBER_ID)).willReturn(Optional.of(otherAdmin));
			AdminMemberStatusChangeRequest request = new AdminMemberStatusChangeRequest(MemberStatus.SUSPENDED);

			// when & then
			assertThatThrownBy(() -> adminMemberService.changeStatus(ADMIN_ID, MEMBER_ID, request))
					.isInstanceOf(BusinessException.class)
					.extracting("errorCode")
					.isEqualTo(ErrorCode.ADMIN_CANNOT_MODIFY_ADMIN);
			verify(adminAuditLogService, never()).record(any(), any(), any(), any(), any());
		}

		@Test
		@DisplayName("존재하지 않는 회원이면 MEMBER_NOT_FOUND 예외를 던진다")
		void throwsWhenMemberNotFound() {
			// given
			given(memberRepository.findById(MEMBER_ID)).willReturn(Optional.empty());
			AdminMemberStatusChangeRequest request = new AdminMemberStatusChangeRequest(MemberStatus.SUSPENDED);

			// when & then
			assertThatThrownBy(() -> adminMemberService.changeStatus(ADMIN_ID, MEMBER_ID, request))
					.isInstanceOf(BusinessException.class)
					.extracting("errorCode")
					.isEqualTo(ErrorCode.MEMBER_NOT_FOUND);
		}

		@Test
		@DisplayName("탈퇴한 회원이면 MEMBER_WITHDRAWN 예외를 던진다")
		void throwsWhenMemberWithdrawn() {
			// given
			Member withdrawn = MemberFixture.withId(MemberFixture.createWithdrawn(), MEMBER_ID);
			given(memberRepository.findById(MEMBER_ID)).willReturn(Optional.of(withdrawn));
			AdminMemberStatusChangeRequest request = new AdminMemberStatusChangeRequest(MemberStatus.SUSPENDED);

			// when & then
			assertThatThrownBy(() -> adminMemberService.changeStatus(ADMIN_ID, MEMBER_ID, request))
					.isInstanceOf(BusinessException.class)
					.extracting("errorCode")
					.isEqualTo(ErrorCode.MEMBER_WITHDRAWN);
		}

		@Test
		@DisplayName("이미 요청한 상태와 같으면 감사 로그를 남기지 않고 refresh token 도 삭제하지 않는다")
		void doesNothingWhenStatusUnchanged() {
			// given
			Member member = MemberFixture.withId(MemberFixture.create(), MEMBER_ID);
			given(memberRepository.findById(MEMBER_ID)).willReturn(Optional.of(member));
			given(memberQueryMapper.findActivitySummary(eq(MEMBER_ID), any()))
					.willReturn(new AdminMemberActivitySummary(0L, BigDecimal.ZERO, 0L));
			given(orderQueryMapper.findMyOrders(any())).willReturn(List.of());
			AdminMemberStatusChangeRequest request = new AdminMemberStatusChangeRequest(MemberStatus.ACTIVE);

			// when
			adminMemberService.changeStatus(ADMIN_ID, MEMBER_ID, request);

			// then
			verify(adminAuditLogService, never()).record(any(), any(), any(), any(), any());
			verify(refreshTokenRepository, never()).deleteByMemberId(any());
		}

		@Test
		@DisplayName("SUSPENDED 로 바꾸면 refresh token 을 삭제하고 감사 로그를 남긴다")
		void suspendsMemberAndDeletesRefreshToken() {
			// given
			Member member = MemberFixture.withId(MemberFixture.create(), MEMBER_ID);
			given(memberRepository.findById(MEMBER_ID)).willReturn(Optional.of(member));
			given(memberQueryMapper.findActivitySummary(eq(MEMBER_ID), any()))
					.willReturn(new AdminMemberActivitySummary(0L, BigDecimal.ZERO, 0L));
			given(orderQueryMapper.findMyOrders(any())).willReturn(List.of());
			AdminMemberStatusChangeRequest request = new AdminMemberStatusChangeRequest(MemberStatus.SUSPENDED);

			// when
			AdminMemberDetailResponse response = adminMemberService.changeStatus(ADMIN_ID, MEMBER_ID, request);

			// then
			assertThat(response.status()).isEqualTo(MemberStatus.SUSPENDED);
			verify(refreshTokenRepository).deleteByMemberId(MEMBER_ID);
			verify(adminAuditLogService).record(ADMIN_ID, AdminAuditAction.MEMBER_STATUS_CHANGE,
					AdminAuditTargetType.MEMBER, MEMBER_ID, "ACTIVE->SUSPENDED");
		}

		@Test
		@DisplayName("ACTIVE 로 바꾸면 refresh token 을 삭제하지 않고 감사 로그를 남긴다")
		void activatesMemberWithoutDeletingRefreshToken() {
			// given
			Member member = MemberFixture.withId(MemberFixture.createSuspended(), MEMBER_ID);
			given(memberRepository.findById(MEMBER_ID)).willReturn(Optional.of(member));
			given(memberQueryMapper.findActivitySummary(eq(MEMBER_ID), any()))
					.willReturn(new AdminMemberActivitySummary(0L, BigDecimal.ZERO, 0L));
			given(orderQueryMapper.findMyOrders(any())).willReturn(List.of());
			AdminMemberStatusChangeRequest request = new AdminMemberStatusChangeRequest(MemberStatus.ACTIVE);

			// when
			AdminMemberDetailResponse response = adminMemberService.changeStatus(ADMIN_ID, MEMBER_ID, request);

			// then
			assertThat(response.status()).isEqualTo(MemberStatus.ACTIVE);
			verify(refreshTokenRepository, never()).deleteByMemberId(anyLong());
			verify(adminAuditLogService).record(ADMIN_ID, AdminAuditAction.MEMBER_STATUS_CHANGE,
					AdminAuditTargetType.MEMBER, MEMBER_ID, "SUSPENDED->ACTIVE");
		}
	}
}
