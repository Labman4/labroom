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

package com.elpsykongroo.gateway.config;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpMethod;
import org.springframework.security.config.Customizer;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.security.oauth2.jwt.NimbusJwtDecoder;
import org.springframework.security.web.SecurityFilterChain;

import static org.springframework.security.config.Customizer.withDefaults;

@Configuration(proxyBeanMethods = false)
public class SecurityConfig {

	@Autowired
	private ServiceConfig serviceConfig;

	@Bean
	public SecurityFilterChain securityFilterChain(HttpSecurity http) throws Exception {
		http
				.cors(csrf->csrf.disable())
				.cors(withDefaults())
				.sessionManagement(sm ->
						sm.sessionCreationPolicy(SessionCreationPolicy.STATELESS)
				)
				.oauth2ResourceServer(oauth2 ->
						oauth2.jwt(Customizer.withDefaults()))
				.authorizeHttpRequests((authorize) -> authorize
						.requestMatchers(HttpMethod.GET, "/actuator/**").permitAll()
						.requestMatchers(HttpMethod.GET,"/public/**").permitAll()
						.anyRequest().authenticated());
		return http.build();
	}

	@Bean
	public JwtDecoder jwtDecoder() {
		return NimbusJwtDecoder.withJwkSetUri(serviceConfig.getUrl().getAuth() + "/oauth2/jwks").build();
	}
}