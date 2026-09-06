package com.groove.product.repository;

import java.util.List;
import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;

import com.groove.product.entity.Label;

public interface LabelRepository extends JpaRepository<Label, Long> {

	List<Label> findAllByOrderByNameAsc();

	Optional<Label> findFirstByNameOrderByIdAsc(String name);
}
