package com.groove.file.config;

import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Configuration;

/**
 * {@link FileProperties} 바인딩 전용. {@code UploadResourceConfig} 는 prod 프로파일에서 제외되므로
 * 프로파일과 무관하게 바인딩이 살아있도록 별도 설정 클래스로 분리한다.
 */
@Configuration
@EnableConfigurationProperties(FileProperties.class)
public class FileConfig {
}
