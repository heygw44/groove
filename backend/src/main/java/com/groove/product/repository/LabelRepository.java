package com.groove.product.repository;

import org.springframework.data.jpa.repository.JpaRepository;

import com.groove.product.entity.Label;

public interface LabelRepository extends JpaRepository<Label, Long> {
}
