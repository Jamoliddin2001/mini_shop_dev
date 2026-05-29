package com.shop.security;

import com.shop.security.JwtTokenProvider.AuthenticatedUser;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.web.authentication.WebAuthenticationDetailsSource;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.util.List;
import java.util.Optional;

/**
 * Authenticates requests carrying a {@code Authorization: Bearer <jwt>} header.
 *
 * <p>On a valid token it populates the {@link SecurityContextHolder} with an authentication
 * built straight from the token claims (no DB lookup per request — stateless). Missing or
 * invalid tokens are simply left unauthenticated; the {@code ExceptionTranslationFilter} then
 * returns 401 via {@link JwtAuthenticationEntryPoint}. The filter never throws on a bad token.</p>
 */
@Component
public class JwtAuthenticationFilter extends OncePerRequestFilter {

    private static final Logger log = LoggerFactory.getLogger(JwtAuthenticationFilter.class);
    private static final String HEADER = "Authorization";
    private static final String PREFIX = "Bearer ";

    private final JwtTokenProvider tokenProvider;

    public JwtAuthenticationFilter(JwtTokenProvider tokenProvider) {
        this.tokenProvider = tokenProvider;
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response,
                                    FilterChain filterChain) throws ServletException, IOException {
        Optional<String> token = extractBearerToken(request);
        if (token.isEmpty()) {
            log.debug("No bearer token on {} {} -> anonymous", request.getMethod(), request.getRequestURI());
            filterChain.doFilter(request, response);
            return;
        }

        Optional<AuthenticatedUser> principal = tokenProvider.parse(token.get());
        if (principal.isPresent() && SecurityContextHolder.getContext().getAuthentication() == null) {
            AuthenticatedUser user = principal.get();
            var authorities = List.of(new SimpleGrantedAuthority("ROLE_" + user.role().name()));
            var authentication = new UsernamePasswordAuthenticationToken(user.email(), null, authorities);
            authentication.setDetails(new WebAuthenticationDetailsSource().buildDetails(request));
            SecurityContextHolder.getContext().setAuthentication(authentication);
            log.debug("Authenticated request with roles={}", authorities);
        } else if (principal.isEmpty()) {
            log.debug("Invalid bearer token on {} {} -> anonymous", request.getMethod(), request.getRequestURI());
        }

        filterChain.doFilter(request, response);
    }

    private Optional<String> extractBearerToken(HttpServletRequest request) {
        String header = request.getHeader(HEADER);
        if (header != null && header.startsWith(PREFIX)) {
            return Optional.of(header.substring(PREFIX.length()));
        }
        return Optional.empty();
    }
}
