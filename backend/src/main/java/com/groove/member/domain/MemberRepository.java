package com.groove.member.domain;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

/**
 * 회원 영속성.
 *
 * <p><b>패턴 A — soft delete 점유 정책</b>: 가입 중복 검사는 탈퇴자도 포함한 {@link #existsByEmail}.
 * 로그인/조회 같은 활성 회원 검색은 {@link #findByEmailAndDeletedAtIsNull} 사용.
 */
public interface MemberRepository extends JpaRepository<Member, Long> {

    boolean existsByEmail(String email);

    Optional<Member> findByEmailAndDeletedAtIsNull(String email);

    Optional<Member> findByIdAndDeletedAtIsNull(Long id);
}
