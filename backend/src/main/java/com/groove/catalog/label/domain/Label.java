package com.groove.catalog.label.domain;

import com.groove.common.persistence.BaseTimeEntity;
import org.hibernate.annotations.BatchSize;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

/**
 * 음반 레이블 엔티티 (ERD §4.5).
 *
 * <p>Genre 와 동일한 단순 카탈로그 구조. {@code name} 길이 한도만 100 자로 더 길다.
 *
 * <p>클래스 레벨 {@code @BatchSize}(#235): Album keyset 스크롤(fluent {@code scroll}) 경로의 label
 * LAZY 프록시 N+1 을 IN 쿼리 1회로 흡수한다(offset 의 {@code @EntityGraph} 경로는 무영향).
 * 자세한 배경은 {@link com.groove.catalog.artist.domain.Artist} Javadoc 참조.
 */
@Entity
@Table(name = "label")
@BatchSize(size = 100)
public class Label extends BaseTimeEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "name", nullable = false, length = 100, unique = true)
    private String name;

    protected Label() {
    }

    private Label(String name) {
        this.name = name;
    }

    public static Label create(String name) {
        return new Label(name);
    }

    public void rename(String newName) {
        this.name = newName;
    }

    public Long getId() {
        return id;
    }

    public String getName() {
        return name;
    }
}
