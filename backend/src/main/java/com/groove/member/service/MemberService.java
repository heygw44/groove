package com.groove.member.service;

import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.groove.auth.service.SessionRevoker;
import com.groove.global.common.BusinessException;
import com.groove.global.common.ErrorCode;
import com.groove.member.dto.MemberResponse;
import com.groove.member.dto.MemberUpdateRequest;
import com.groove.member.dto.MemberWithdrawRequest;
import com.groove.member.dto.PasswordChangeRequest;
import com.groove.member.entity.Member;
import com.groove.member.repository.MemberRepository;

import lombok.RequiredArgsConstructor;

/** 내 정보 조회/수정, 비밀번호 변경, 탈퇴를 담당한다. */
@Service
@Transactional(readOnly = true)
@RequiredArgsConstructor
public class MemberService {

	private final MemberRepository memberRepository;
	private final SessionRevoker sessionRevoker;
	private final PasswordEncoder passwordEncoder;
	private final PasswordAttemptGuard passwordAttemptGuard;

	public MemberResponse getMyInfo(Long memberId) {
		return MemberResponse.from(findActiveMember(memberId));
	}

	@Transactional
	public MemberResponse updateNickname(Long memberId, MemberUpdateRequest request) {
		Member member = findActiveMember(memberId);
		member.changeNickname(request.nickname());
		return MemberResponse.from(member);
	}

	@Transactional
	public void changePassword(Long memberId, PasswordChangeRequest request) {
		Member member = findActiveMember(memberId);
		verifyPassword(member, request.currentPassword());
		member.changePassword(passwordEncoder.encode(request.newPassword()));
		// refresh 쿠키 Path 가 /api/v1/auth 라 이 요청에선 현재 세션을 구분할 수 없어 전부 폐기한다.
		sessionRevoker.revokeAll(memberId);
	}

	@Transactional
	public void withdraw(Long memberId, MemberWithdrawRequest request) {
		Member member = findActiveMember(memberId);
		verifyPassword(member, request.password());
		member.withdraw();
		// 탈퇴 즉시 재발급·기존 access token 사용을 막기 위해 모든 세션을 폐기한다.
		sessionRevoker.revokeAll(memberId);
	}

	// 잠금을 먼저 보고, 불일치는 회원 단위로 센다. 성공하면 카운터를 비워 정상 사용자가 누적 실패로 막히지 않게 한다.
	private void verifyPassword(Member member, String rawPassword) {
		Long memberId = member.getId();
		passwordAttemptGuard.checkNotLocked(memberId);
		if (!passwordEncoder.matches(rawPassword, member.getPassword())) {
			passwordAttemptGuard.recordFailure(memberId);
			throw new BusinessException(ErrorCode.MEMBER_PASSWORD_MISMATCH);
		}
		passwordAttemptGuard.reset(memberId);
	}

	private Member findActiveMember(Long memberId) {
		Member member = memberRepository.findById(memberId)
				.orElseThrow(() -> new BusinessException(ErrorCode.MEMBER_NOT_FOUND));
		member.validateActive();
		return member;
	}
}
