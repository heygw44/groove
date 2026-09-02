package com.groove.global.config;

import org.mybatis.spring.annotation.MapperScan;
import org.springframework.context.annotation.Configuration;

/**
 * MyBatis 매퍼 인터페이스 스캔 범위.
 * 매퍼는 com.groove.{domain}.mapper 패키지에만 두고, 읽기 전용 복잡 쿼리만 담당한다.
 * XML 위치와 camelCase 매핑은 application.yml(mybatis.*)에서 설정.
 */
@Configuration
@MapperScan(basePackages = "com.groove", annotationClass = org.apache.ibatis.annotations.Mapper.class)
public class MyBatisConfig {
}
