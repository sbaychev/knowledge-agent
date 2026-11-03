
package com.stefan.knowledgeagent.config;

import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Profile;
import org.springframework.beans.factory.annotation.Value;

import org.springframework.http.HttpMethod;
import org.springframework.security.config.Customizer;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.userdetails.User;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.provisioning.InMemoryUserDetailsManager;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationConverter;
import org.springframework.security.oauth2.server.resource.authentication.JwtGrantedAuthoritiesConverter;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.core.convert.converter.Converter;

import java.util.Collection;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

@Configuration(proxyBeanMethods = false)
@EnableWebSecurity
@EnableMethodSecurity
public class SecurityConfiguration {

    @Bean
    PasswordEncoder passwordEncoder() {
        return new BCryptPasswordEncoder();
    }

    @Configuration(proxyBeanMethods = false)
    @Profile("local")
    static class LocalSecurity {

        @Bean
        UserDetailsService localApprovalUsers(
                PasswordEncoder encoder,
                @Value("${security.local.agent.username:agent}") String agentUsername,
                @Value("${security.local.agent.password:local-agent-change-me}") String agentPassword,
                @Value("${security.local.approver.username:approver}") String approverUsername,
                @Value("${security.local.approver.password:local-approver-change-me}") String approverPassword
        ) {
            if (agentUsername.equals(approverUsername)) {
                throw new IllegalStateException(
                        "Local agent and approver usernames must be different."
                );
            }

            return new InMemoryUserDetailsManager(
                    User.withUsername(agentUsername)
                            .password(encoder.encode(agentPassword))
                            .roles("AGENT")
                            .build(),
                    User.withUsername(approverUsername)
                            .password(encoder.encode(approverPassword))
                            .roles("APPROVER")
                            .build()
            );
        }

        @Bean
        SecurityFilterChain localSecurityFilterChain(HttpSecurity http) {
            http
                    .csrf(AbstractHttpConfigurer::disable)
                    .sessionManagement(session -> session
                            .sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                    .authorizeHttpRequests(authorize -> authorize
                            .requestMatchers(
                                    HttpMethod.POST,
                                    "/api/v1/payments/*/refunds"
                            ).hasRole("APPROVER")
                            .requestMatchers(
                                    HttpMethod.POST,
                                    "/api/v1/approvals/refunds"
                            ).hasAnyRole("AGENT", "APPROVER")
                            .requestMatchers(
                                    HttpMethod.GET,
                                    "/api/v1/approvals"
                            ).hasRole("APPROVER")
                            .requestMatchers(
                                    HttpMethod.GET,
                                    "/api/v1/approvals/*"
                            ).hasAnyRole("AGENT", "APPROVER")
                            .requestMatchers(
                                    HttpMethod.POST,
                                    "/api/v1/approvals/*/approve",
                                    "/api/v1/approvals/*/reject"
                            ).hasRole("APPROVER")
                            .anyRequest().permitAll()
                    )
                    .httpBasic(Customizer.withDefaults());

            return http.build();
        }
    }

    @Configuration(proxyBeanMethods = false)
    @Profile("test")
    static class TestSecurity {

        @Bean
        UserDetailsService testApprovalUsers(PasswordEncoder encoder) {

            return new InMemoryUserDetailsManager(
                    User.withUsername("agent")
                            .password(encoder.encode("agent-test-password"))
                            .roles("AGENT")
                            .build(),
                    User.withUsername("approver")
                            .password(encoder.encode("approver-test-password"))
                            .roles("APPROVER")
                            .build()
            );
        }

        @Bean
        SecurityFilterChain testSecurityFilterChain(HttpSecurity http) {
            http
                    .csrf(AbstractHttpConfigurer::disable)
                    .sessionManagement(session -> session
                            .sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                    .authorizeHttpRequests(authorize -> authorize
                            .requestMatchers(
                                    HttpMethod.POST,
                                    "/api/v1/payments/*/refunds"
                            ).hasRole("APPROVER")
                            .requestMatchers(
                                    HttpMethod.POST,
                                    "/api/v1/approvals/refunds"
                            ).hasAnyRole("AGENT", "APPROVER")
                            .requestMatchers(
                                    HttpMethod.GET,
                                    "/api/v1/approvals"
                            ).hasRole("APPROVER")
                            .requestMatchers(
                                    HttpMethod.GET,
                                    "/api/v1/approvals/*"
                            ).hasAnyRole("AGENT", "APPROVER")
                            .requestMatchers(
                                    HttpMethod.POST,
                                    "/api/v1/approvals/*/approve",
                                    "/api/v1/approvals/*/reject"
                            ).hasRole("APPROVER")
                            .anyRequest().permitAll()
                    )
                    .httpBasic(Customizer.withDefaults());

            return http.build();
        }
    }

    @Configuration(proxyBeanMethods = false)
    @Profile("!local & !test")
    static class DeployedSecurity {

        @Bean
        @ConditionalOnProperty(
                name = "spring.security.oauth2.resourceserver.jwt.issuer-uri"
        )
        SecurityFilterChain jwtApiSecurityFilterChain(HttpSecurity http) {
            http
                    .csrf(AbstractHttpConfigurer::disable)
                    .sessionManagement(session -> session
                            .sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                    .authorizeHttpRequests(authorize -> authorize
                            .requestMatchers(
                                    "/actuator/health",
                                    "/actuator/health/**",
                                    "/actuator/info"
                            ).permitAll()
                            .anyRequest().authenticated()
                    )
                    .oauth2ResourceServer(oauth2 -> oauth2
                            .jwt(jwt -> jwt.jwtAuthenticationConverter(
                                    jwtAuthenticationConverter()
                            ))
                    );

            return http.build();
        }

        @Bean
        @ConditionalOnMissingBean(name = "jwtApiSecurityFilterChain")
        SecurityFilterChain unconfiguredSecurityFilterChain(HttpSecurity http) {

            // Fail closed for API traffic until an issuer is configured;
            // health probes remain available.
            http
                    .csrf(AbstractHttpConfigurer::disable)
                    .sessionManagement(session -> session
                            .sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                    .authorizeHttpRequests(authorize -> authorize
                            .requestMatchers(
                                    "/actuator/health",
                                    "/actuator/health/**",
                                    "/actuator/info"
                            ).permitAll()
                            .anyRequest().denyAll()
                    );

            return http.build();

        }

        private static JwtAuthenticationConverter jwtAuthenticationConverter() {

            JwtGrantedAuthoritiesConverter scopes = new JwtGrantedAuthoritiesConverter();

            Converter<Jwt, Collection<GrantedAuthority>> authoritiesConverter =
                    jwt -> {

                        Set<GrantedAuthority> authorities =
                                new LinkedHashSet<>();

                        Collection<GrantedAuthority> scopeAuthorities =
                                scopes.convert(jwt);

                        if (scopeAuthorities != null) {

                            authorities.addAll(scopeAuthorities);
                        }

                        List<String> roles = jwt.getClaimAsStringList("roles");

                        if (roles != null) {
                            roles.stream()
                                    .map(role -> role.startsWith("ROLE_")
                                            ? role
                                            : "ROLE_" + role)
                                    .map(SimpleGrantedAuthority::new)
                                    .forEach(authorities::add);
                        }

                        return authorities;
                    };

            JwtAuthenticationConverter converter = new JwtAuthenticationConverter();

            converter.setJwtGrantedAuthoritiesConverter(authoritiesConverter);

            return converter;
        }
    }
}
