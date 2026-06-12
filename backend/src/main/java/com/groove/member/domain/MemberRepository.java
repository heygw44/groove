package com.groove.member.domain;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Collection;
import java.util.Optional;

/**
 * 회원 영속성.
 *
 * 패턴 A — soft delete 점유 정책: 가입 중복 검사는 탈퇴자도 포함한 existsByEmailHashIn (#170 부터 평문
 * existsByEmail 대신 정규화 이메일 해시로 점유를 판정한다 — 탈퇴 익명화가 평문을 치환해도 해시는 보존돼
 * 재가입이 차단된다). #186 에서 해시를 HMAC(v1) 으로 전환하면서, 마이그레이션 이전 탈퇴 회원(재계산
 * 불가한 SHA-256 보존)의 점유도 잡도록 v1·legacy 두 해시를 함께 조회한다. 로그인/조회 같은 활성 회원
 * 검색은 findByEmailAndDeletedAtIsNull 가 평문으로 수행한다(컬럼 collation 이 대소문자를 무시).
 */
public interface MemberRepository extends JpaRepository<Member, Long> {

    /**
     * 가입 중복 검사 (#170 패턴 A, #186) — EmailHasher 가 만든 v1(HMAC)·legacy(SHA-256) 해시를 함께 받아
     * 점유 여부를 본다. 탈퇴(익명화) 회원도 email_hash 가 보존되므로 포함되며, 마이그레이션 이전 탈퇴
     * 회원(legacy 해시 보존)도 legacy 인자로 함께 차단된다.
     */
    boolean existsByEmailHashIn(Collection<String> emailHashes);

    /**
     * 단일 해시 점유 검사. 통합 테스트·기타 단일 조회용 — 가입 경로의 양방향 검사는 existsByEmailHashIn 를
     * 쓴다.
     */
    boolean existsByEmailHash(String emailHash);

    Optional<Member> findByEmailAndDeletedAtIsNull(String email);

    Optional<Member> findByIdAndDeletedAtIsNull(Long id);

    /**
     * 활성 회원 존재 검사 (#187) — 엔티티 로드 없이 deleted_at IS NULL 행의 존재만 본다. 토큰 유효기간 내
     * 탈퇴(soft delete)한 회원의 주문/장바구니/쿠폰/결제 쓰기를 차단하는 경량 가드용이다 (Member
     * 엔티티가 필요한 리뷰 작성은 findByIdAndDeletedAtIsNull 를 쓴다).
     */
    boolean existsByIdAndDeletedAtIsNull(Long id);
}
