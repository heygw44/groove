package com.groove.member.application;

import com.groove.member.domain.Member;
import com.groove.member.domain.MemberRepository;
import com.groove.member.exception.MemberEmailDuplicatedException;
import com.groove.member.exception.MemberNotFoundException;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class MemberService {

    private final MemberRepository memberRepository;
    private final PasswordEncoder passwordEncoder;

    public MemberService(MemberRepository memberRepository, PasswordEncoder passwordEncoder) {
        this.memberRepository = memberRepository;
        this.passwordEncoder = passwordEncoder;
    }

    /**
     * 회원가입.
     *
     * <p>이메일 중복(soft delete 포함) 검사 → BCrypt 해시 → 영속화.
     *
     * <p>선체크({@code existsByEmail})와 {@code save} 사이에 동시 가입 요청이 끼어들 수 있다.
     * DB UNIQUE 제약이 최종 방어선이며, 위반 시 {@link DataIntegrityViolationException} 을
     * 도메인 예외로 변환해 409 응답이 보장된다.
     *
     * @throws MemberEmailDuplicatedException 동일 이메일이 이미 존재
     */
    @Transactional
    public Member signup(SignupCommand command) {
        if (memberRepository.existsByEmail(command.email())) {
            throw new MemberEmailDuplicatedException();
        }
        String passwordHash = passwordEncoder.encode(command.password());
        Member member = Member.register(
                command.email(),
                passwordHash,
                command.name(),
                command.phone()
        );
        try {
            return memberRepository.saveAndFlush(member);
        } catch (DataIntegrityViolationException ex) {
            throw new MemberEmailDuplicatedException(ex);
        }
    }

    /**
     * 내 정보 조회 (#76, API.md §3.2 — GET /members/me). 활성 회원만 — soft delete 제외.
     *
     * @throws MemberNotFoundException 활성 회원이 없음 (탈퇴 후 토큰 만료 전 윈도 등)
     */
    @Transactional(readOnly = true)
    public Member getMyInfo(Long memberId) {
        return memberRepository.findByIdAndDeletedAtIsNull(memberId)
                .orElseThrow(MemberNotFoundException::new);
    }

    /**
     * 내 정보 부분 수정 (#76, API.md §3.2 — PATCH /members/me).
     *
     * <p>name·phone 만 변경하며, 미전송(null) 필드는 유지된다 ({@link Member#updateProfile}).
     * 더티 체킹으로 트랜잭션 커밋 시 반영되므로 별도 save 호출은 불필요하다.
     *
     * @throws MemberNotFoundException 활성 회원이 없음
     */
    @Transactional
    public Member updateMyInfo(Long memberId, UpdateProfileCommand command) {
        Member member = memberRepository.findByIdAndDeletedAtIsNull(memberId)
                .orElseThrow(MemberNotFoundException::new);
        member.updateProfile(command.name(), command.phone());
        return member;
    }
}
