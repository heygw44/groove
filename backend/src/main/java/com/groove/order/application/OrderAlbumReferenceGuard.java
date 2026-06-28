package com.groove.order.application;

import com.groove.catalog.album.application.AlbumReferenceGuard;
import com.groove.order.domain.OrderRepository;
import org.springframework.stereotype.Component;

/** 주문 항목이 앨범을 참조하는지 확인하는 {@link AlbumReferenceGuard} 구현(catalog→order 역참조 차단). */
@Component
public class OrderAlbumReferenceGuard implements AlbumReferenceGuard {

    private final OrderRepository orderRepository;

    public OrderAlbumReferenceGuard(OrderRepository orderRepository) {
        this.orderRepository = orderRepository;
    }

    @Override
    public boolean isReferenced(Long albumId) {
        return orderRepository.existsByAlbumId(albumId);
    }
}
