/*
 * Copyright 2022-2022 the original author or authors.
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

package com.elpsykongroo.storage.config;

import jakarta.servlet.http.HttpServletRequest;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpMethod;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.AuthenticationManagerResolver;
import org.springframework.security.authentication.ProviderManager;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.security.oauth2.jwt.NimbusJwtDecoder;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationProvider;
import org.springframework.security.oauth2.server.resource.authentication.OpaqueTokenAuthenticationProvider;
import org.springframework.security.oauth2.server.resource.introspection.OpaqueTokenIntrospector;
import org.springframework.security.oauth2.server.resource.introspection.SpringOpaqueTokenIntrospector;
import org.springframework.security.web.SecurityFilterChain;

import static org.springframework.security.config.Customizer.withDefaults;

@Configuration(proxyBeanMethods = false)
public class SecurityConfig {

	@Autowired
	private ServiceConfig serviceConfig;

	@Autowired
	private RequestConfig requestConfig;

	@Bean
	AuthenticationManagerResolver<HttpServletRequest> tokenAuthenticationManagerResolver
			(JwtDecoder jwtDecoder, OpaqueTokenIntrospector opaqueTokenIntrospector) {
		AuthenticationManager jwt = new ProviderManager(new JwtAuthenticationProvider(jwtDecoder));
		AuthenticationManager opaque = new ProviderManager(
				new OpaqueTokenAuthenticationProvider(opaqueTokenIntrospector));
		return request -> {
			String auth = request.getHeader("Authorization");

			if (auth == null || !auth.startsWith("Bearer ")) {
				return null;
			}

			String token = auth.substring(7);

			return token.chars().filter(c -> c == '.').count() == 2
					? jwt
					: opaque;
		};
	}

	@Bean
	public OpaqueTokenIntrospector opaqueTokenIntrospector() {
		return SpringOpaqueTokenIntrospector
				.withIntrospectionUri(serviceConfig.getOauth2().getIntrospectionUri())
				.clientId(serviceConfig.getOauth2().getClientId())
				.clientSecret(serviceConfig.getOauth2().getClientSecret()).build();
	}

	@Bean
	public SecurityFilterChain securityFilterChain(HttpSecurity http) throws Exception {
		http
				.cors(csrf->csrf.disable())
				.cors(withDefaults())
				.sessionManagement(sm ->
						sm.sessionCreationPolicy(SessionCreationPolicy.STATELESS)
				)
				.oauth2ResourceServer(oauth2 ->
						oauth2.authenticationManagerResolver(tokenAuthenticationManagerResolver(jwtDecoder(),
								opaqueTokenIntrospector())))
				.authorizeHttpRequests((authorize) -> authorize
						.requestMatchers(HttpMethod.GET, "/actuator/**").permitAll()
						.requestMatchers(requestConfig.getPath().getPermit()).permitAll()
						.anyRequest().authenticated());
		return http.build();
	}

	@Bean
	public JwtDecoder jwtDecoder() {
		return NimbusJwtDecoder.withJwkSetUri(serviceConfig.getUrl().getAuth() + "/oauth2/jwks").build();
	}
}