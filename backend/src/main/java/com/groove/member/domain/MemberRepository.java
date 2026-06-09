package com.groove.member.domain;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

/**
 * 회원 영속성.
 *
 * <p><b>패턴 A — soft delete 점유 정책</b>: 가입 중복 검사는 탈퇴자도 포함한 {@link #existsByEmailHash}
 * (#170 부터 평문 {@code existsByEmail} 대신 정규화 이메일 해시로 점유를 판정한다 — 탈퇴 익명화가 평문을
 * 치환해도 해시는 보존돼 재가입이 차단된다). 로그인/조회 같은 활성 회원 검색은
 * {@link #findByEmailAndDeletedAtIsNull} 가 평문으로 수행한다(컬럼 collation 이 대소문자를 무시).
 */
public interface MemberRepository extends JpaRepository<Member, Long> {

    /**
     * 가입 중복 검사 (#170, 패턴 A) — 정규화 이메일 해시({@link Member#hashEmail})로 점유 여부를 본다.
     * 탈퇴(익명화) 회원도 {@code email_hash} 가 보존되므로 포함된다.
     */
    boolean existsByEmailHash(String emailHash);

    Optional<Member> findByEmailAndDeletedAtIsNull(String email);

    Optional<Member> findByIdAndDeletedAtIsNull(Long id);
}
