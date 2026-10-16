package com.tothework.shared.security;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.util.List;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.filter.OncePerRequestFilter;

/** {@code Authorization: Bearer} 액세스 토큰을 검증하고 현재 사용자를 SecurityContext에 넣는다. */
public class JwtAuthenticationFilter extends OncePerRequestFilter {

    private static final String PREFIX = "Bearer ";
    private static final List<SimpleGrantedAuthority> USER_ROLE = List.of(new SimpleGrantedAuthority("ROLE_USER"));

    private final JwtTokens jwtTokens;
    private final AuthenticatedUserLoader userLoader;

    public JwtAuthenticationFilter(JwtTokens jwtTokens, AuthenticatedUserLoader userLoader) {
        this.jwtTokens = jwtTokens;
        this.userLoader = userLoader;
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain chain)
            throws ServletException, IOException {
        String header = request.getHeader("Authorization");
        if (header != null && header.startsWith(PREFIX)) {
            String token = header.substring(PREFIX.length());
            if (jwtTokens.isValid(token, JwtTokens.Type.ACCESS)) {
                // 토큰은 유효해도 계정이 없어졌으면(탈퇴) 미인증으로 두어 401이 나가게 한다.
                userLoader.loadByEmail(jwtTokens.emailOf(token)).ifPresent(user ->
                        SecurityContextHolder.getContext().setAuthentication(
                                new UsernamePasswordAuthenticationToken(user, null, USER_ROLE)));
            }
        }
        chain.doFilter(request, response);
    }
}
