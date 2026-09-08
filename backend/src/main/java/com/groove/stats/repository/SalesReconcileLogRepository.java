package com.groove.stats.repository;

import org.springframework.data.jpa.repository.JpaRepository;

import com.groove.stats.entity.SalesReconcileLog;

public interface SalesReconcileLogRepository extends JpaRepository<SalesReconcileLog, Long> {
}
