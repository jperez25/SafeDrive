package com.qnt.safedrive.gateway;

import java.time.Clock;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import tools.jackson.databind.json.JsonMapper;

@Configuration
@EnableConfigurationProperties(GatewayProperties.class)
class GatewayConfig {

    @Bean
    JwtService jwtService(GatewayProperties p, JsonMapper mapper) {
        return new JwtService(p.jwtPrivateKey(), p.issuer(), p.audience(), p.tokenTtlSeconds(), Clock.systemUTC(), mapper);
    }

    @Bean
    Routes routes(GatewayProperties p) {
        return new Routes(p);
    }

    @Bean
    Downstream downstream(GatewayProperties p) {
        return new Downstream(p.connectTimeoutMs(), p.requestTimeoutMs());
    }

    @Bean
    PasswordEncoder passwordEncoder() {
        return new BCryptPasswordEncoder();
    }
}
