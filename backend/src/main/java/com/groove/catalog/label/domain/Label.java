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
 * 음반 레이블 엔티티. name 최대 100 자의 단순 카탈로그 메타.
 *
 * <p>클래스 레벨 @BatchSize: label LAZY 프록시 N+1 을 IN 쿼리 1회로 흡수한다.
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
