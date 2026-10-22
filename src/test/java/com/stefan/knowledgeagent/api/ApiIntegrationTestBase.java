package com.stefan.knowledgeagent.api;

import com.stefan.knowledgeagent.PostgresTestConfiguration;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.resttestclient.TestRestTemplate;
import org.springframework.boot.resttestclient.autoconfigure.AutoConfigureTestRestTemplate;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.HttpHeaders;
import org.springframework.jdbc.core.JdbcTemplate;

import java.util.UUID;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@AutoConfigureTestRestTemplate
@Import(PostgresTestConfiguration.class)
abstract class ApiIntegrationTestBase {

    @Autowired
    protected TestRestTemplate http;

    @Autowired
    protected JdbcTemplate jdbc;

    protected Long createCustomer() {

        String unique = UUID.randomUUID().toString();

        return jdbc.queryForObject(
                """
                INSERT INTO customer (
                    external_id, name, email, status, created_at
                )
                VALUES (?, ?, ?, ?, CURRENT_TIMESTAMP)
                RETURNING id
                """,
                Long.class,
                "API-IT-" + unique,
                "API Integration Test",
                unique + "@example.test",
                "ACTIVE"
        );
    }

    protected HttpHeaders jsonHeaders() {

        HttpHeaders headers = new HttpHeaders();
        headers.set("Content-Type", "application/json");

        return headers;
    }
}
