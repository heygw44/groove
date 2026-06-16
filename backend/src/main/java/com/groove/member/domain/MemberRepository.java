package com.groove.member.domain;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Collection;
import java.util.Optional;

/**
 * 회원 영속성. 가입 중복 검사는 탈퇴자도 포함한 existsByEmailHashIn(정규화 이메일 해시로 점유 판정),
 * 로그인/조회 같은 활성 회원 검색은 findByEmailAndDeletedAtIsNull 가 평문으로 수행한다.
 */
public interface MemberRepository extends JpaRepository<Member, Long> {

    /** 가입 중복 검사 — v1(HMAC)·legacy(SHA-256) 해시를 함께 받아 점유 여부를 본다. 탈퇴 회원도 포함. */
    boolean existsByEmailHashIn(Collection<String> emailHashes);

    /** 단일 해시 점유 검사. */
    boolean existsByEmailHash(String emailHash);

    Optional<Member> findByEmailAndDeletedAtIsNull(String email);

    Optional<Member> findByIdAndDeletedAtIsNull(Long id);

    /** 활성 회원 존재 검사 — 엔티티 로드 없이 deleted_at IS NULL 행의 존재만 본다. */
    boolean existsByIdAndDeletedAtIsNull(Long id);
}
