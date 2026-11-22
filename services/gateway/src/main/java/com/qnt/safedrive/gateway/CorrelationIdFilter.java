package com.qnt.safedrive.gateway;

import com.qnt.safedrive.commons.CorrelationId;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

/** Edge of the platform: takes the caller's correlation ID or creates one, and binds it to the logs. */
@Component
class CorrelationIdFilter extends OncePerRequestFilter {

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain chain)
            throws ServletException, IOException {
        String id = CorrelationId.orGenerate(request.getHeader(CorrelationId.HTTP_HEADER));
        response.setHeader(CorrelationId.HTTP_HEADER, id);
        try (CorrelationId.Scope ignored = CorrelationId.bind(id)) {
            chain.doFilter(request, response);
        }
    }
}
