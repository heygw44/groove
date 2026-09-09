package com.groove.catalog.config;

import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Configuration;

@Configuration
@EnableConfigurationProperties(CatalogResyncProperties.class)
public class CatalogResyncConfig {
}
