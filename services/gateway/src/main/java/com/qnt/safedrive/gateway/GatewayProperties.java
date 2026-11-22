package com.qnt.safedrive.gateway;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties("safedrive.gateway")
record GatewayProperties(
        String issuer,
        String audience,
        long tokenTtlSeconds,
        String jwtPrivateKey,
        int maxBodyBytes,
        int connectTimeoutMs,
        int requestTimeoutMs,
        String ingestUrl,
        String scoringUrl,
        String ratingUrl,
        String rewardsUrl,
        String claimsUrl) {}
