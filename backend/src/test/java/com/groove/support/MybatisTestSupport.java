package com.groove.support;

import org.mybatis.spring.boot.test.autoconfigure.AutoConfigureMybatis;

/**
 * MyBatis 매퍼 슬라이스 테스트 공통 베이스. {@code @MybatisTest} 만으로는 JPA 자동설정이 빠져
 * 엔티티로 픽스처를 넣을 EntityManager 를 못 얻으므로, {@link DataJpaTestSupport}(JPA 슬라이스 + 컨테이너)
 * 위에 {@code @AutoConfigureMybatis} 로 매퍼/SqlSession 설정만 얹는다.
 */
@AutoConfigureMybatis
public abstract class MybatisTestSupport extends DataJpaTestSupport {
}
