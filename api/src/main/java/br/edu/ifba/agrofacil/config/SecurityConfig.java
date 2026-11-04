// Regras de acesso HTTP e configuração do JWT (HS256) usado como token de acesso (doc 2 §1.7.1).
package br.edu.ifba.agrofacil.config;

import br.edu.ifba.agrofacil.common.error.ErrorCode;
import br.edu.ifba.agrofacil.common.security.AppVersionFilter;
import br.edu.ifba.agrofacil.common.security.CurrentUserProvider;
import br.edu.ifba.agrofacil.common.security.ProblemWriter;
import com.nimbusds.jose.jwk.source.ImmutableSecret;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpMethod;
import org.springframework.security.config.Customizer;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.crypto.argon2.Argon2PasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.oauth2.jose.jws.MacAlgorithm;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.security.oauth2.jwt.JwtEncoder;
import org.springframework.security.oauth2.jwt.NimbusJwtDecoder;
import org.springframework.security.oauth2.jwt.NimbusJwtEncoder;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationConverter;
import org.springframework.security.oauth2.server.resource.authentication.JwtGrantedAuthoritiesConverter;
import org.springframework.security.oauth2.server.resource.web.authentication.BearerTokenAuthenticationFilter;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.header.writers.ReferrerPolicyHeaderWriter;

/**
 * Sem sessão nem CSRF (a API usa {@code Authorization: Bearer}); o refresh por cookie tem proteção
 * própria em {@code /auth/refresh}. CORS fica desligado: o painel e a API estão na mesma origem
 * (doc 2 §1.3).
 */
@Configuration
public class SecurityConfig {

  /** Rotas abertas: autenticação, health check (Docker e túnel) e Swagger (só no perfil dev). */
  private static final String[] PUBLIC_PATHS = {
    "/auth/**",
    "/actuator/health",
    "/actuator/health/**",
    "/openapi.json",
    "/openapi.json/**",
    "/docs",
    "/docs/**",
    "/swagger-ui/**",
    "/error"
  };

  /** Rotas do agricultor que o coordenador não usa. */
  private static final String[] FARMER_PATHS = {"/sync/**", "/me/device"};

  /** Rotas de qualquer usuário autenticado; o escopo é conferido nos serviços. */
  private static final String[] AUTHENTICATED_PATHS = {
    "/me", "/me/**", "/files/**", "/catalog/units", "/municipalities", "/reports/field-notebook"
  };

  @Bean
  SecurityFilterChain securityFilterChain(
      HttpSecurity http, AppProperties props, ProblemWriter problemWriter) throws Exception {
    http.csrf(csrf -> csrf.disable())
        .cors(cors -> cors.disable())
        .sessionManagement(s -> s.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
        .httpBasic(basic -> basic.disable())
        .formLogin(form -> form.disable())
        .authorizeHttpRequests(auth ->
            auth.requestMatchers(PUBLIC_PATHS)
                .permitAll()
                .requestMatchers(FARMER_PATHS)
                .hasRole("FARMER")
                .requestMatchers(AUTHENTICATED_PATHS)
                .authenticated()
                // O agricultor também apaga os próprios anexos (doc 1 §3.6).
                .requestMatchers(HttpMethod.DELETE, "/attachments/*")
                .authenticated()
                .anyRequest()
                .hasRole("COORDINATOR"))
        .oauth2ResourceServer(o ->
            o.jwt(jwt -> jwt.jwtAuthenticationConverter(jwtAuthConverter()))
                .authenticationEntryPoint((req, res, ex) ->
                    problemWriter.write(
                        req,
                        res,
                        isExpired(ex) ? ErrorCode.TOKEN_EXPIRED : ErrorCode.UNAUTHENTICATED))
                .accessDeniedHandler(
                    (req, res, ex) -> problemWriter.write(req, res, ErrorCode.FORBIDDEN)))
        .exceptionHandling(e ->
            e.authenticationEntryPoint(
                    (req, res, ex) -> problemWriter.write(req, res, ErrorCode.UNAUTHENTICATED))
                .accessDeniedHandler(
                    (req, res, ex) -> problemWriter.write(req, res, ErrorCode.FORBIDDEN)))
        .addFilterBefore(
            new AppVersionFilter(props.minMobileVersion(), problemWriter),
            BearerTokenAuthenticationFilter.class)
        .headers(h ->
            h.contentTypeOptions(Customizer.withDefaults())
                .referrerPolicy(
                    r -> r.policy(ReferrerPolicyHeaderWriter.ReferrerPolicy.NO_REFERRER)));
    return http.build();
  }

  @Bean
  JwtDecoder jwtDecoder(AppProperties props) {
    return NimbusJwtDecoder.withSecretKey(props.jwt().secretKey())
        .macAlgorithm(MacAlgorithm.HS256)
        .build();
  }

  @Bean
  JwtEncoder jwtEncoder(AppProperties props) {
    return new NimbusJwtEncoder(new ImmutableSecret<>(props.jwt().secretKey()));
  }

  /** Argon2id para senhas, códigos de acesso e códigos 2FA (ADR-04). */
  @Bean
  PasswordEncoder passwordEncoder() {
    return Argon2PasswordEncoder.defaultsForSpringSecurity_v5_8();
  }

  /** A claim {@code role} vira a autoridade {@code ROLE_FARMER} ou {@code ROLE_COORDINATOR}. */
  private static JwtAuthenticationConverter jwtAuthConverter() {
    var authorities = new JwtGrantedAuthoritiesConverter();
    authorities.setAuthoritiesClaimName(CurrentUserProvider.CLAIM_ROLE);
    authorities.setAuthorityPrefix("ROLE_");
    var converter = new JwtAuthenticationConverter();
    converter.setJwtGrantedAuthoritiesConverter(authorities);
    return converter;
  }

  /** O Spring Security não tem um tipo próprio para "expirado"; a descrição traz a palavra. */
  private static boolean isExpired(Exception ex) {
    return ex.getMessage() != null && ex.getMessage().toLowerCase().contains("expired");
  }
}
