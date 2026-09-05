package com.groove.member.service;

import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.groove.auth.repository.RefreshTokenRepository;
import com.groove.global.common.BusinessException;
import com.groove.global.common.ErrorCode;
import com.groove.member.dto.MemberResponse;
import com.groove.member.dto.MemberUpdateRequest;
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
	private final RefreshTokenRepository refreshTokenRepository;
	private final PasswordEncoder passwordEncoder;

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
		if (!passwordEncoder.matches(request.currentPassword(), member.getPassword())) {
			throw new BusinessException(ErrorCode.MEMBER_PASSWORD_MISMATCH);
		}
		member.changePassword(passwordEncoder.encode(request.newPassword()));
	}

	@Transactional
	public void withdraw(Long memberId) {
		Member member = findActiveMember(memberId);
		member.withdraw();
		// 탈퇴 즉시 재발급을 막기 위해 세션도 폐기한다.
		refreshTokenRepository.deleteByMemberId(memberId);
	}

	private Member findActiveMember(Long memberId) {
		Member member = memberRepository.findById(memberId)
				.orElseThrow(() -> new BusinessException(ErrorCode.MEMBER_NOT_FOUND));
		member.validateActive();
		return member;
	}
}
