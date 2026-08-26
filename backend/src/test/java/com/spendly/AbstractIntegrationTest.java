package com.spendly;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Shared Postgres for the integration tests. The container is static so all
 * subclasses run against one instance instead of starting a database per class.
 */
@SpringBootTest
@AutoConfigureMockMvc
@Testcontainers(disabledWithoutDocker = true)
public abstract class AbstractIntegrationTest {

    @Container
    static final PostgreSQLContainer<?> POSTGRES = new PostgreSQLContainer<>("postgres:18-alpine")
            .withDatabaseName("spendly")
            .withUsername("spendly")
            .withPassword("spendly");

    @DynamicPropertySource
    static void datasourceProps(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", POSTGRES::getJdbcUrl);
        registry.add("spring.datasource.username", POSTGRES::getUsername);
        registry.add("spring.datasource.password", POSTGRES::getPassword);
    }

    @Autowired
    protected MockMvc mockMvc;

    @Autowired
    protected ObjectMapper objectMapper;

    protected String registerAndGetToken(String email) throws Exception {
        MvcResult result = mockMvc.perform(post("/api/auth/register")
                        .contentType(org.springframework.http.MediaType.APPLICATION_JSON)
                        .content("""
                                {"email":"%s","password":"Secret123!"}
                                """.formatted(email)))
                .andExpect(status().isCreated())
                .andReturn();
        return tokenFrom(result);
    }

    protected String tokenFrom(MvcResult result) throws Exception {
        return objectMapper.readTree(result.getResponse().getContentAsString())
                .get("accessToken").asText();
    }
}
