package com.groove.member.repository;

import java.util.List;
import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;

import com.groove.member.entity.Member;
import com.groove.member.entity.MemberRole;

public interface MemberRepository extends JpaRepository<Member, Long> {

	Optional<Member> findByEmail(String email);

	boolean existsByEmail(String email);

	List<Member> findAllByRole(MemberRole role);
}
