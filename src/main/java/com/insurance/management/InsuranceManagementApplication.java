package com.insurance.management;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.data.jpa.repository.config.EnableJpaAuditing;
import org.springframework.scheduling.annotation.EnableAsync;
import org.springframework.transaction.annotation.EnableTransactionManagement;

/**
 * Main Spring Boot Application for Insurance Management System
 * 
 * Features:
 * - RESTful API for web and mobile clients
 * - JPA for database operations
 * - JWT-based authentication
 * - Async processing support
 * - Audit logging
 */
@SpringBootApplication
@EnableJpaAuditing
@EnableAsync
@EnableTransactionManagement
public class InsuranceManagementApplication {

    public static void main(String[] args) {
        SpringApplication.run(InsuranceManagementApplication.class, args);
    }
}
