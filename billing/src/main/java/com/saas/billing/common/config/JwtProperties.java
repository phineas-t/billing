package com.saas.billing.common.config;

import lombok.*;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;


@Component
@ConfigurationProperties(prefix="security.jwt")
@Getter
@Setter
public class JwtProperties {

    private String secret;
    private int accessTokenMinutes;
    private int refreshTokenDays;
}
