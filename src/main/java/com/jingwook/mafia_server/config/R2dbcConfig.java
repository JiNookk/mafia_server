package com.jingwook.mafia_server.config;

import org.springframework.context.annotation.Configuration;
import org.springframework.data.r2dbc.repository.config.EnableR2dbcRepositories;
import org.springframework.transaction.annotation.EnableTransactionManagement;

@Configuration
@EnableR2dbcRepositories(basePackages = "com.jingwook.mafia_server.repositories")
@EnableTransactionManagement
public class R2dbcConfig {
    // Spring Boot의 자동 설정을 활용하여 ConnectionFactory, TransactionManager 등이 자동으로 구성됩니다.
    // application.properties의 설정이 자동으로 적용됩니다.
}