package com.groove.catalog.genre.domain;

import com.groove.common.persistence.BaseTimeEntity;
import org.hibernate.annotations.BatchSize;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

/**
 * 장르 엔티티. 이름만 가진 카탈로그 메타이며 UNIQUE 는 DB 제약에 위임한다.
 *
 * <p>클래스 레벨 @BatchSize: genre LAZY 프록시 N+1 을 IN 쿼리 1회로 흡수한다.
 */
@Entity
@Table(name = "genre")
@BatchSize(size = 100)
public class Genre extends BaseTimeEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "name", nullable = false, length = 50, unique = true)
    private String name;

    protected Genre() {
    }

    private Genre(String name) {
        this.name = name;
    }

    /**
     * 정적 팩토리.
     */
    public static Genre create(String name) {
        return new Genre(name);
    }

    /**
     * 이름 변경.
     */
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
