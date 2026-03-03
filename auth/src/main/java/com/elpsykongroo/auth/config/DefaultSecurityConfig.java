/*
 * Copyright 2020-2022 the original author or authors.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      https://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.elpsykongroo.auth.config;

import com.elpsykongroo.auth.filter.CsrfSessionFilter;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.annotation.Order;
import org.springframework.http.HttpMethod;
import org.springframework.security.config.annotation.authentication.builders.AuthenticationManagerBuilder;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.crypto.factory.PasswordEncoderFactories;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.logout.SecurityContextLogoutHandler;
import org.springframework.security.web.authentication.www.BasicAuthenticationFilter;
import org.springframework.security.web.csrf.CsrfToken;
import org.springframework.security.web.csrf.CsrfTokenRequestAttributeHandler;
import org.springframework.security.web.csrf.CsrfTokenRequestHandler;
import org.springframework.security.web.csrf.HttpSessionCsrfTokenRepository;
import org.springframework.security.web.csrf.XorCsrfTokenRequestAttributeHandler;
import org.springframework.security.web.savedrequest.HttpSessionRequestCache;
import org.springframework.security.web.servlet.util.matcher.PathPatternRequestMatcher;
import org.springframework.util.StringUtils;
import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.cors.CorsConfigurationSource;
import org.springframework.web.cors.UrlBasedCorsConfigurationSource;
import org.springframework.security.config.Customizer;

import java.util.function.Supplier;

import static org.springframework.security.config.Customizer.withDefaults;

@EnableWebSecurity
@Configuration(proxyBeanMethods = false)
public class DefaultSecurityConfig {
	@Autowired
    private UserDetailsService userDetailsService;

	@Autowired
	private ServiceConfig serviceConfig;

	@Bean
	@Order(999)
	public SecurityFilterChain defaultSecurityFilterChain(HttpSecurity http) throws Exception {
		HttpSessionRequestCache requestCache = new HttpSessionRequestCache();
		requestCache.setRequestMatcher(PathPatternRequestMatcher.pathPattern("/oauth2/authorize/**"));
		http
				.cors(withDefaults())
				.formLogin(f->f.disable())
				.logout((logout) -> logout
						.clearAuthentication(true)
						.invalidateHttpSession(true)
						.addLogoutHandler(new SecurityContextLogoutHandler())
				)
				.requestCache(
						cache -> cache.requestCache(requestCache)
				)
				.csrf(csrf->csrf
						.csrfTokenRequestHandler(new SpaCsrfTokenRequestHandler())
						.ignoringRequestMatchers("/authenticator/add"))
				.addFilterAfter(new CsrfSessionFilter(new HttpSessionCsrfTokenRepository()), BasicAuthenticationFilter.class)
				.authorizeHttpRequests((authorize) -> authorize
						.requestMatchers(HttpMethod.GET,"/email/verify/**").permitAll()
						.requestMatchers(
								"/login/**",
								"/welcome",
								"/register",
								"/finishAuth").permitAll()
						.anyRequest().authenticated());

		return http.build();
	}

	@Bean
	@Order(0)
	public SecurityFilterChain tmpChain(HttpSecurity http) throws Exception {
		http.securityMatcher("/tmp", "/email/tmp")
				.cors(withDefaults())
				.csrf(csrf->csrf.disable())
				.authorizeHttpRequests(auth -> auth.anyRequest().permitAll());
		return http.build();
	}

	@Bean
	@Order(900)
	public SecurityFilterChain authFilterChain(HttpSecurity http) throws Exception {
		http.securityMatcher("/auth/**", "/authenticator/add")
				.cors(withDefaults())
				.sessionManagement(sm ->
						sm.sessionCreationPolicy(SessionCreationPolicy.STATELESS)
				)
				.oauth2ResourceServer(oauth2 ->
						oauth2.opaqueToken(Customizer.withDefaults()))
				.authorizeHttpRequests((authorize) -> authorize
				.requestMatchers("/auth/user/authority/**").permitAll()
				.requestMatchers("/auth/user/list").hasAuthority("admin")
				.requestMatchers("/auth/user/**").authenticated()
				.requestMatchers("/auth/**").hasAuthority("admin")
				.requestMatchers("/authenticator/add").authenticated());

		return http.build();
	}

	@Bean
	public PasswordEncoder passwordEncoder() {
		return PasswordEncoderFactories.createDelegatingPasswordEncoder();
	}

	@Autowired
	public void configureGlobal(AuthenticationManagerBuilder auth) throws Exception {
		auth.userDetailsService(userDetailsService);
	}

	@Bean
	public HttpSessionRequestCache httpSessionRequestCache() {
		return new HttpSessionRequestCache();
	}

	@Bean
	public CorsConfigurationSource corsConfigurationSource() {
		UrlBasedCorsConfigurationSource source = new UrlBasedCorsConfigurationSource();
		CorsConfiguration config = new CorsConfiguration();
		config.addAllowedHeader("*");
		config.addAllowedMethod("*");
		config.addExposedHeader("X-CSRF-TOKEN");
		config.addAllowedOrigin(serviceConfig.getUrl().getLoginPage());
		config.setAllowCredentials(true);
		source.registerCorsConfiguration("/**", config);
		return source;
	}

	private static final class SpaCsrfTokenRequestHandler implements CsrfTokenRequestHandler {
		private final CsrfTokenRequestAttributeHandler plain = new CsrfTokenRequestAttributeHandler();
		private final CsrfTokenRequestAttributeHandler xor = new XorCsrfTokenRequestAttributeHandler();

		SpaCsrfTokenRequestHandler() {
			this.xor.setCsrfRequestAttributeName((String)null);
		}

		@Override
		public void handle(HttpServletRequest request, HttpServletResponse response, Supplier<CsrfToken> csrfToken) {
			/*
			 * Always use XorCsrfTokenRequestAttributeHandler to provide BREACH protection of
			 * the CsrfToken when it is rendered in the response body.
			 */
			this.xor.handle(request, response, csrfToken);
		}

		@Override
		public String resolveCsrfTokenValue(HttpServletRequest request, CsrfToken csrfToken) {
			String headerValue = request.getHeader(csrfToken.getHeaderName());
			return (StringUtils.hasText(headerValue) ? this.plain : this.xor).resolveCsrfTokenValue(request, csrfToken);
		}
	}
}
