package com.groove.member.repository;

import java.util.List;
import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import com.groove.member.entity.Address;

public interface AddressRepository extends JpaRepository<Address, Long> {

	List<Address> findAllByMemberIdOrderByIdAsc(Long memberId);

	Optional<Address> findByIdAndMemberId(Long id, Long memberId);

	long countByMemberId(Long memberId);

	@Query("select a from Address a where a.member.id = :memberId and a.isDefault = true")
	Optional<Address> findDefaultByMemberId(@Param("memberId") Long memberId);
}
