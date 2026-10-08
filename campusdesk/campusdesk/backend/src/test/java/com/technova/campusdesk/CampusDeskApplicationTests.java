package com.technova.campusdesk;

import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;

/** Smoke test: the whole context (JPA, security, JWT, bootstrap) starts against an in-memory H2 database. */
@SpringBootTest
@ActiveProfiles("test")
class CampusDeskApplicationTests {

    @Test
    void contextLoads() {
    }
}
