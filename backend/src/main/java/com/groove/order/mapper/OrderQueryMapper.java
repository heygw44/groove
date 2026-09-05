package com.groove.order.mapper;

import java.util.List;

import org.apache.ibatis.annotations.Mapper;

import com.groove.order.dto.AdminOrderSearchCondition;
import com.groove.order.dto.AdminOrderSummaryResponse;
import com.groove.order.dto.OrderSearchCondition;
import com.groove.order.dto.OrderSummaryResponse;

/** 마이페이지·관리자 주문 목록 조회 전용. 상세는 JPA 엔티티 그래프({@code OrderRepository})를 쓴다. */
@Mapper
public interface OrderQueryMapper {

	List<OrderSummaryResponse> findMyOrders(OrderSearchCondition condition);

	long countMyOrders(OrderSearchCondition condition);

	List<AdminOrderSummaryResponse> findAdminOrders(AdminOrderSearchCondition condition);

	long countAdminOrders(AdminOrderSearchCondition condition);
}
