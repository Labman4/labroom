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

import com.elpsykongroo.auth.security.convert.PublicClientRefreshTokenAuthenticationConverter;
import com.elpsykongroo.auth.security.convert.PublicRevokeAuthenticationConverter;
import com.elpsykongroo.auth.security.provider.PublicClientRefreshTokenAuthenticationProvider;
import com.elpsykongroo.auth.security.provider.WebAuthnAuthenticationProvider;
import com.nimbusds.jose.jwk.source.JWKSource;
import com.nimbusds.jose.proc.SecurityContext;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.lang.Nullable;
import org.springframework.security.config.Customizer;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.OAuth2AuthorizationServerConfiguration;
import org.springframework.security.crypto.keygen.Base64StringKeyGenerator;
import org.springframework.security.crypto.keygen.StringKeyGenerator;
import org.springframework.security.oauth2.client.JdbcOAuth2AuthorizedClientService;
import org.springframework.security.oauth2.client.OAuth2AuthorizedClientManager;
import org.springframework.security.oauth2.client.OAuth2AuthorizedClientProvider;
import org.springframework.security.oauth2.client.OAuth2AuthorizedClientProviderBuilder;
import org.springframework.security.oauth2.client.OAuth2AuthorizedClientService;
import org.springframework.security.oauth2.client.registration.ClientRegistrationRepository;
import org.springframework.security.oauth2.client.web.AuthenticatedPrincipalOAuth2AuthorizedClientRepository;
import org.springframework.security.oauth2.client.web.DefaultOAuth2AuthorizedClientManager;
import org.springframework.security.oauth2.client.web.OAuth2AuthorizedClientRepository;
import org.springframework.security.oauth2.core.OAuth2RefreshToken;
import org.springframework.security.oauth2.core.oidc.OidcScopes;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.security.oauth2.jwt.NimbusJwtEncoder;
import org.springframework.security.oauth2.server.authorization.client.RegisteredClientRepository;
import org.springframework.security.oauth2.server.authorization.settings.AuthorizationServerSettings;
import org.springframework.security.oauth2.server.authorization.token.DelegatingOAuth2TokenGenerator;
import org.springframework.security.oauth2.server.authorization.token.JwtEncodingContext;
import org.springframework.security.oauth2.server.authorization.token.JwtGenerator;
import org.springframework.security.oauth2.server.authorization.token.OAuth2AccessTokenGenerator;
import org.springframework.security.oauth2.server.authorization.token.OAuth2RefreshTokenGenerator;
import org.springframework.security.oauth2.server.authorization.token.OAuth2TokenClaimsContext;
import org.springframework.security.oauth2.server.authorization.token.OAuth2TokenContext;
import org.springframework.security.oauth2.server.authorization.token.OAuth2TokenCustomizer;
import org.springframework.security.oauth2.server.authorization.token.OAuth2TokenGenerator;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.session.HttpSessionEventPublisher;
import java.time.Clock;
import java.time.Instant;
import java.util.Base64;

@Configuration(proxyBeanMethods = false)
public class AuthorizationServerConfig {
	@Value("${ISSUER_URL}")
	private String issuerUrl;

	@Autowired
	RegisteredClientRepository registeredClientRepository;

	@Autowired
	OAuth2TokenCustomizer<JwtEncodingContext> jwtCustomizer;

	@Autowired
	OAuth2TokenCustomizer<OAuth2TokenClaimsContext> accessTokenCustomizer;

	@Autowired
	public JWKSource<SecurityContext> jwkSource;

	@Bean
	@Order(Ordered.HIGHEST_PRECEDENCE)
	public SecurityFilterChain authorizationServerSecurityFilterChain(HttpSecurity http) throws Exception {

////		Function<OidcUserInfoAuthenticationContext, OidcUserInfo> userInfoMapper = (context) -> {
////			OidcUserInfoAuthenticationToken authentication = context.getAuthentication();
////			JwtAuthenticationToken principal = (JwtAuthenticationToken) authentication.getPrincipal();
////			return new OidcUserInfo(principal.getToken().getClaims());
////		};
//
////		OAuth2ClientAuthorizationRequestResolver resolver = new OAuth2ClientAuthorizationRequestResolver(clientRegistrationRepository);
////		resolver.setAuthorizationRequestCustomizer(OAuth2AuthorizationRequestCustomizers.withPkce());
//

		http.oauth2AuthorizationServer((authorizationServer) -> {
					http.securityMatcher(authorizationServer.getEndpointsMatcher())
							.cors(Customizer.withDefaults())
							.csrf((csrf)-> csrf
									.ignoringRequestMatchers(authorizationServer.getEndpointsMatcher()));

					authorizationServer.oidc(Customizer.withDefaults())
							.clientAuthentication(clientAuthentication -> clientAuthentication
								.authenticationConverter(new PublicRevokeAuthenticationConverter(registeredClientRepository))
								.authenticationProvider(new WebAuthnAuthenticationProvider()))
							.tokenRevocationEndpoint(tokenRevocationEndpoint -> tokenRevocationEndpoint
								.revocationRequestConverter(new PublicRevokeAuthenticationConverter(registeredClientRepository)))
							.clientAuthentication(clientAuthentication -> clientAuthentication
								.authenticationConverter(new PublicClientRefreshTokenAuthenticationConverter())
								.authenticationProvider(new PublicClientRefreshTokenAuthenticationProvider(registeredClientRepository)));
				});
		return http.build();
	}

	@Bean
	public JwtDecoder jwtDecoder(JWKSource<SecurityContext> jwkSource) {
		return OAuth2AuthorizationServerConfiguration.jwtDecoder(jwkSource);
	}

	@Bean
	public AuthorizationServerSettings authorizationServerSettings() {
		return AuthorizationServerSettings.builder().issuer(issuerUrl).build();
	}

	@Bean
	public OAuth2AuthorizedClientService authorizedClientService(
			JdbcTemplate jdbcTemplate,
			ClientRegistrationRepository clientRegistrationRepository) {
		return new JdbcOAuth2AuthorizedClientService(jdbcTemplate, clientRegistrationRepository);
	}

	@Bean
	public OAuth2AuthorizedClientRepository authorizedClientRepository(
			OAuth2AuthorizedClientService authorizedClientService) {
		return new AuthenticatedPrincipalOAuth2AuthorizedClientRepository(authorizedClientService);
	}

	@Bean
	OAuth2AuthorizedClientManager authorizedClientManager(ClientRegistrationRepository clientRegistrationRepository,
														  OAuth2AuthorizedClientRepository authorizedClientRepository) {
		OAuth2AuthorizedClientProvider authorizedClientProvider = OAuth2AuthorizedClientProviderBuilder
				.builder()
				.clientCredentials()
				.authorizationCode()
				.refreshToken()
				.build();
		DefaultOAuth2AuthorizedClientManager authorizedClientManager = new DefaultOAuth2AuthorizedClientManager(clientRegistrationRepository, authorizedClientRepository);
		authorizedClientManager.setAuthorizedClientProvider(authorizedClientProvider);
		return authorizedClientManager;
	}

	@Bean
	public HttpSessionEventPublisher httpSessionEventPublisher() {
		return new HttpSessionEventPublisher();
	}

	@Bean
	public OAuth2TokenGenerator<?> tokenGenerator() {
		JwtGenerator jwtGenerator = new JwtGenerator(new NimbusJwtEncoder(jwkSource));
		jwtGenerator.setJwtCustomizer(jwtCustomizer);
		OAuth2AccessTokenGenerator accessTokenGenerator = new OAuth2AccessTokenGenerator();
		accessTokenGenerator.setAccessTokenCustomizer(accessTokenCustomizer);
		OAuth2RefreshTokenGenerator refreshTokenGenerator = new OAuth2RefreshTokenGenerator();
		OAuth2TokenGenerator<OAuth2RefreshToken> customRefreshTokenGenerator = new CustomRefreshTokenGenerator();
		return new DelegatingOAuth2TokenGenerator(new OAuth2TokenGenerator[]{jwtGenerator, accessTokenGenerator, refreshTokenGenerator, customRefreshTokenGenerator});
	}

	private static final class CustomRefreshTokenGenerator implements OAuth2TokenGenerator<OAuth2RefreshToken> {
		private final StringKeyGenerator refreshTokenGenerator = new Base64StringKeyGenerator(Base64.getUrlEncoder().withoutPadding(), 96);
		private Clock clock = Clock.systemUTC();

		@Nullable
		@Override
		public OAuth2RefreshToken generate(OAuth2TokenContext context) {
			if (context.getAuthorizedScopes().contains(OidcScopes.OPENID) &&
					!context.getAuthorizedScopes().contains("offline_access")) {
				return null;
			}
			Instant issuedAt = this.clock.instant();
			Instant expiresAt = issuedAt.plus(context.getRegisteredClient().getTokenSettings().getRefreshTokenTimeToLive());
			return new OAuth2RefreshToken(this.refreshTokenGenerator.generateKey(), issuedAt, expiresAt);
		}
	}
}
