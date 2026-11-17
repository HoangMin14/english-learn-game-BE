package com.aws.alphabetuniverse;

import jakarta.persistence.EntityManagerFactory;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import software.amazon.awssdk.services.cognitoidentityprovider.CognitoIdentityProviderClient;
import software.amazon.awssdk.services.polly.PollyClient;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.presigner.S3Presigner;

import javax.sql.DataSource;
import java.sql.SQLException;

import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.verifyNoInteractions;

@SpringBootTest(properties = "spring.config.location=classpath:/application-test.properties")
// Test properties take precedence even if database variables exist in the shell.
@TestPropertySource("classpath:/application-test.properties")
class BackendApplicationTests {

    @Autowired
    private DataSource dataSource;

    @Autowired
    private EntityManagerFactory entityManagerFactory;

    @MockitoBean
    private CognitoIdentityProviderClient cognitoClient;

    @MockitoBean
    private PollyClient pollyClient;

    @MockitoBean
    private S3Client s3Client;

    @MockitoBean
    private S3Presigner s3Presigner;

    @Test
    void contextLoads() throws SQLException {
        try (var connection = dataSource.getConnection()) {
            assertTrue(connection.getMetaData().getURL().startsWith("jdbc:h2:mem:"));
        }
        assertTrue(entityManagerFactory.isOpen());
        verifyNoInteractions(cognitoClient, pollyClient, s3Client, s3Presigner);
    }

}
