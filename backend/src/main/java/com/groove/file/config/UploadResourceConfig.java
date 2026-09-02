package com.groove.file.config;

import java.io.File;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Profile;
import org.springframework.web.servlet.config.annotation.ResourceHandlerRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

import lombok.RequiredArgsConstructor;

/**
 * 로컬 디스크에 저장된 업로드 파일을 {@code /uploads/**} 로 직접 서빙한다.
 * 운영은 Nginx 가 같은 경로를 가로채므로 이 핸들러가 필요 없다.
 * {@code WebMvcConfigurer} 를 이 클래스에 직접 구현하면 {@code @WebMvcTest} 슬라이스가 해당 타입을 자동 포함시켜
 * {@link FileProperties} 가 없는 다른 슬라이스 테스트까지 깨지므로, 빈 메서드로만 노출한다.
 */
@Configuration
@Profile("!prod")
@RequiredArgsConstructor
public class UploadResourceConfig {

	private final FileProperties fileProperties;

	@Bean
	public WebMvcConfigurer uploadResourceHandler() {
		String location = "file:" + new File(fileProperties.uploadPath()).getAbsolutePath() + "/";
		return new WebMvcConfigurer() {
			@Override
			public void addResourceHandlers(ResourceHandlerRegistry registry) {
				registry.addResourceHandler("/uploads/**")
						.addResourceLocations(location);
			}
		};
	}
}
