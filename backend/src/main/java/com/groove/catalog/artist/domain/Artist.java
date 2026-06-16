package com.groove.catalog.artist.domain;

import com.groove.common.persistence.BaseTimeEntity;
import org.hibernate.annotations.BatchSize;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

/**
 * 아티스트 엔티티. name 에 UNIQUE 를 두지 않고 ID 로만 식별한다. description 은 NULL 허용.
 * 클래스 레벨 @BatchSize 는 LAZY 프록시를 IN 쿼리 1회로 일괄 초기화한다.
 */
@Entity
@Table(name = "artist")
@BatchSize(size = 100)
public class Artist extends BaseTimeEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "name", nullable = false, length = 200)
    private String name;

    @Column(name = "description", columnDefinition = "TEXT")
    private String description;

    protected Artist() {
    }

    private Artist(String name, String description) {
        this.name = name;
        this.description = description;
    }

    /** 정적 팩토리. */
    public static Artist create(String name, String description) {
        return new Artist(name, description);
    }

    /** 이름과 설명을 갱신한다 (PUT 전체 교체). description 이 null 이면 지움. */
    public void update(String newName, String newDescription) {
        this.name = newName;
        this.description = newDescription;
    }

    public Long getId() {
        return id;
    }

    public String getName() {
        return name;
    }

    public String getDescription() {
        return description;
    }
}
