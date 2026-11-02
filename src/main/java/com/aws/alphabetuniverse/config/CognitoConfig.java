package com.aws.alphabetuniverse.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import software.amazon.awssdk.core.client.config.ClientOverrideConfiguration;
import software.amazon.awssdk.regions.Region;
import software.amazon.awssdk.services.cognitoidentityprovider.CognitoIdentityProviderClient;

import java.time.Duration;

@Configuration
public class CognitoConfig {
    @Bean
    public CognitoIdentityProviderClient cognitoClient(){
        return CognitoIdentityProviderClient.builder()
                .region(Region.US_EAST_1)
                .overrideConfiguration(ClientOverrideConfiguration.builder()
                        .apiCallAttemptTimeout(Duration.ofSeconds(3))
                        .apiCallTimeout(Duration.ofSeconds(8))
                        .build())
                .build();
    }
}
