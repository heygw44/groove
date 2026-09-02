package com.groove.product.repository;

import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;

import com.groove.product.entity.Label;

public interface LabelRepository extends JpaRepository<Label, Long> {

	List<Label> findAllByOrderByNameAsc();
}
