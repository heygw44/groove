package com.groove.order.repository;

import com.groove.order.entity.OrderClaimStatus;
import com.groove.order.entity.OrderClaimType;

/** 클레임 유형·상태별 건수 집계 한 행({@link OrderClaimRepository#countByTypeAndStatus()}). */
public interface OrderClaimCountRow {

	OrderClaimType getType();

	OrderClaimStatus getStatus();

	long getCount();
}
