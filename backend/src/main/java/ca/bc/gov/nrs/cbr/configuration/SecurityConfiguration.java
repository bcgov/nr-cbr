package ca.bc.gov.nrs.cbr.configuration;

import jakarta.servlet.http.HttpServletRequest;
import java.util.Arrays;
import java.util.List;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.config.Customizer;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.cors.CorsConfigurationSource;
import org.springframework.web.cors.UrlBasedCorsConfigurationSource;

import ca.bc.gov.nrs.cbr.security.ApiAuthorizationCustomizer;
import ca.bc.gov.nrs.cbr.security.Oauth2SecurityCustomizer;

/**
 * Main security configuration. The API runs as a <strong>stateless</strong> OAuth 2.0 resource
 * server validating BC Gov SSO (Keycloak) access tokens. Follows nr-fspts.
 *
 * <h3>No custom security headers</h3>
 * {@code .headers(...)} is deliberately not customized, which leaves Spring Security's defaults in
 * place — {@code X-Content-Type-Options: nosniff}, {@code X-Frame-Options: DENY},
 * {@code Strict-Transport-Security}, {@code Cache-Control: no-cache} and {@code X-XSS-Protection: 0}.
 * Those are the headers that mean something on a JSON response.
 *
 * <p>The removed {@code HeadersSecurityCustomizer} added a Content-Security-Policy,
 * Permissions-Policy and Referrer-Policy on top. All three are <em>document</em>-scoped: a browser
 * applies them to a page, and this API returns no HTML — no view technology on the classpath, no
 * controller that renders one. They were inert here, and the copy that does the work sits at the
 * Caddy edge in {@code frontend/Caddyfile}, in front of the SPA that is an actual document.
 *
 * <p>If the API ever serves HTML — a rendered error page, a Swagger UI — reinstate a CSP here, or
 * the edge policy will not cover it. Same as nr-fspts, which also leaves the defaults alone.
 */
@Configuration
@EnableWebSecurity
@EnableMethodSecurity
public class SecurityConfiguration {

  @Value("${ca.bc.gov.nrs.frontend.url:http://localhost:3000}")
  private String allowedOrigins;

  @Bean
  public SecurityFilterChain filterChain(
      HttpSecurity http,
      ApiAuthorizationCustomizer apiCustomizer,
      Oauth2SecurityCustomizer oauth2Customizer
  ) throws Exception {

    http
        // CSRF is intentionally disabled: this is a STATELESS OAuth2 resource server (see
        // sessionManagement below) authenticated solely by Bearer JWTs in the Authorization header.
        // CSRF attacks rely on the browser auto-attaching an ambient credential (cookie/session);
        // there is none here, so CSRF does not apply. (Static-analysis flags this generically — it
        // is a false positive for token auth.)
        //
        // The tokens moved out of cookies with the Keycloak migration: services/keycloak.ts keeps
        // them in sessionStorage and the SPA sets the Authorization header itself. Under the
        // previous Amplify CookieStorage arrangement there WAS an ambient credential, which is why
        // the cookie-token CSRF strategy existed — it did not survive its reason.
        .csrf(AbstractHttpConfigurer::disable)
        // No session, therefore no session cookie, therefore nothing the browser attaches on its
        // own. This line is what makes disabling CSRF above safe rather than merely convenient —
        // the two travel together.
        .sessionManagement(sess -> sess.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
        .cors(Customizer.withDefaults())
        .authorizeHttpRequests(apiCustomizer)
        .httpBasic(AbstractHttpConfigurer::disable)
        .formLogin(AbstractHttpConfigurer::disable)
        .oauth2ResourceServer(oauth2Customizer);

    return http.build();
  }

  /**
   * CORS for local development only.
   *
   * <p>In every deployed environment the SPA calls {@code /api/...} as a relative path and Caddy
   * reverse-proxies it to this service ({@code frontend/Caddyfile}), so requests are same-origin
   * and CORS never engages — which is why nr-fspts carries no CORS configuration at all. This bean
   * exists for the case where the Vite dev server is pointed straight at a backend on another port
   * rather than through its own proxy.
   */
  @Bean
  public CorsConfigurationSource corsConfigurationSource() {
    CorsConfiguration configuration = new CorsConfiguration();
    List<String> origins = Arrays.asList(allowedOrigins.split(","));
    configuration.setAllowedOrigins(origins);
    configuration.setAllowedMethods(List.of("GET", "POST", "PUT", "DELETE", "OPTIONS"));
    configuration.setAllowedHeaders(List.of("*"));
    // No credentials: the browser has nothing to send. Tokens ride the Authorization header, which
    // the SPA sets explicitly, and there is no cookie or session to attach — see the CSRF note
    // above. Leaving this true would widen the CORS contract for a credential that does not exist.
    configuration.setAllowCredentials(false);
    configuration.setMaxAge(3600L);

    UrlBasedCorsConfigurationSource source = new UrlBasedCorsConfigurationSource() {
      @Override
      public CorsConfiguration getCorsConfiguration(HttpServletRequest request) {
        return configuration;
      }
    };

    source.registerCorsConfiguration("/**", configuration);
    return source;
  }

}
