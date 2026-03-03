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

package com.elpsykongroo.auth.security.Customizer;

import java.lang.reflect.Field;
import java.security.MessageDigest;
import java.security.cert.X509Certificate;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Base64;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

import com.elpsykongroo.auth.entity.user.Authority;
import com.elpsykongroo.auth.entity.user.Group;
import com.elpsykongroo.auth.entity.user.OidcInfo;
import com.elpsykongroo.auth.entity.user.User;
import com.elpsykongroo.auth.service.custom.AuthorityService;
import com.elpsykongroo.auth.service.custom.GroupService;
import com.elpsykongroo.auth.service.custom.UserService;
import com.nimbusds.jose.jwk.JWK;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.oauth2.core.ClientAuthenticationMethod;
import org.springframework.security.oauth2.core.OAuth2AuthenticationException;
import org.springframework.security.oauth2.core.OAuth2Error;
import org.springframework.security.oauth2.core.OAuth2ErrorCodes;
import org.springframework.security.oauth2.core.oidc.IdTokenClaimNames;
import org.springframework.security.oauth2.core.oidc.OidcIdToken;
import org.springframework.security.oauth2.core.oidc.OidcUserInfo;
import org.springframework.security.oauth2.core.oidc.endpoint.OidcParameterNames;
import org.springframework.security.oauth2.core.oidc.user.OidcUser;
import org.springframework.security.oauth2.core.user.OAuth2User;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.server.authorization.OAuth2TokenType;
import org.springframework.security.oauth2.server.authorization.authentication.OAuth2ClientAuthenticationToken;
import org.springframework.security.oauth2.server.authorization.authentication.OAuth2TokenExchangeActor;
import org.springframework.security.oauth2.server.authorization.authentication.OAuth2TokenExchangeCompositeAuthenticationToken;
import org.springframework.security.oauth2.server.authorization.token.JwtEncodingContext;
import org.springframework.security.oauth2.server.authorization.token.OAuth2TokenClaimNames;
import org.springframework.security.oauth2.server.authorization.token.OAuth2TokenContext;
import org.springframework.security.oauth2.server.authorization.token.OAuth2TokenCustomizer;
import org.springframework.stereotype.Component;
import org.springframework.util.CollectionUtils;

@Slf4j
@Component
public final class FederatedIdentityIdTokenCustomizer implements OAuth2TokenCustomizer<JwtEncodingContext> {

	@Autowired
	private UserService userService;

	@Autowired
	private GroupService groupService;

	@Autowired
	private AuthorityService authorityService;

	private static final Set<String> ID_TOKEN_CLAIMS = Collections.unmodifiableSet(new HashSet<>(Arrays.asList(
			IdTokenClaimNames.ISS,
			IdTokenClaimNames.SUB,
			IdTokenClaimNames.AUD,
			IdTokenClaimNames.EXP,
			IdTokenClaimNames.IAT,
			IdTokenClaimNames.AUTH_TIME,
			IdTokenClaimNames.NONCE,
			IdTokenClaimNames.ACR,
			IdTokenClaimNames.AMR,
			IdTokenClaimNames.AZP,
			IdTokenClaimNames.AT_HASH,
			IdTokenClaimNames.C_HASH
	)));

	@Override
	public void customize(JwtEncodingContext tokenContext) {
		Map<String, Object> cnfClaims = null;

		// Add 'cnf' claim for Mutual-TLS Client Certificate-Bound Access Tokens
		if (OAuth2TokenType.ACCESS_TOKEN.equals(tokenContext.getTokenType())
				&& tokenContext.getAuthorizationGrant() != null && tokenContext.getAuthorizationGrant()
				.getPrincipal() instanceof OAuth2ClientAuthenticationToken clientAuthentication) {

			if ((ClientAuthenticationMethod.TLS_CLIENT_AUTH.equals(clientAuthentication.getClientAuthenticationMethod())
					|| ClientAuthenticationMethod.SELF_SIGNED_TLS_CLIENT_AUTH
					.equals(clientAuthentication.getClientAuthenticationMethod()))
					&& tokenContext.getRegisteredClient().getTokenSettings().isX509CertificateBoundAccessTokens()) {

				X509Certificate[] clientCertificateChain = (X509Certificate[]) clientAuthentication.getCredentials();
				try {
					String sha256Thumbprint = computeSHA256Thumbprint(clientCertificateChain[0]);
					cnfClaims = new HashMap<>();
					cnfClaims.put("x5t#S256", sha256Thumbprint);
				}
				catch (Exception ex) {
					OAuth2Error error = new OAuth2Error(OAuth2ErrorCodes.SERVER_ERROR,
							"Failed to compute SHA-256 Thumbprint for client X509Certificate.", null);
					throw new OAuth2AuthenticationException(error, ex);
				}
			}
		}

		// Add 'cnf' claim for OAuth 2.0 Demonstrating Proof of Possession (DPoP)
		Jwt dPoPProofJwt = tokenContext.get(OAuth2TokenContext.DPOP_PROOF_KEY);
		if (OAuth2TokenType.ACCESS_TOKEN.equals(tokenContext.getTokenType()) && dPoPProofJwt != null) {
			JWK jwk = null;
			@SuppressWarnings("unchecked")
			Map<String, Object> jwkJson = (Map<String, Object>) dPoPProofJwt.getHeaders().get("jwk");
			try {
				jwk = JWK.parse(jwkJson);
			}
			catch (Exception ignored) {
			}
			if (jwk == null) {
				OAuth2Error error = new OAuth2Error(OAuth2ErrorCodes.INVALID_DPOP_PROOF,
						"jwk header is missing or invalid.", null);
				throw new OAuth2AuthenticationException(error);
			}

			try {
				String sha256Thumbprint = jwk.computeThumbprint().toString();
				if (cnfClaims == null) {
					cnfClaims = new HashMap<>();
				}
				cnfClaims.put("jkt", sha256Thumbprint);
			}
			catch (Exception ex) {
				OAuth2Error error = new OAuth2Error(OAuth2ErrorCodes.SERVER_ERROR,
						"Failed to compute SHA-256 Thumbprint for DPoP Proof PublicKey.", null);
				throw new OAuth2AuthenticationException(error, ex);
			}
		}

		if (!CollectionUtils.isEmpty(cnfClaims)) {
			tokenContext.getClaims().claim("cnf", cnfClaims);
		}

		// Add 'act' claim for delegation use case of Token Exchange Grant.
		// If more than one actor is present, we create a chain of delegation by nesting
		// "act" claims.
		if (tokenContext
				.getPrincipal() instanceof OAuth2TokenExchangeCompositeAuthenticationToken compositeAuthenticationToken) {
			Map<String, Object> currentClaims = tokenContext.getClaims().build().getClaims();

			for (OAuth2TokenExchangeActor actor : compositeAuthenticationToken.getActors()) {
				Map<String, Object> actorClaims = actor.getClaims();
				Map<String, Object> actClaim = new LinkedHashMap<>();
				actClaim.put(OAuth2TokenClaimNames.ISS, actorClaims.get(OAuth2TokenClaimNames.ISS));
				actClaim.put(OAuth2TokenClaimNames.SUB, actorClaims.get(OAuth2TokenClaimNames.SUB));
				currentClaims.put("act", Collections.unmodifiableMap(actClaim));
				currentClaims = actClaim;
			}
		}
		if (OidcParameterNames.ID_TOKEN.equals(tokenContext.getTokenType().getValue())) {
			User user = userService.loadUserByUsername(tokenContext.getPrincipal().getName());
			String userId = user.getId();
			List<Authority> authorityList = authorityService.userAuthority(userId);
			Map<String, Object> customClaims = new HashMap<>();
			Map<String, Object> info = user.getUserInfo();
			OidcUserInfo userInfo;
			if (info != null) {
				userInfo = new OidcUserInfo(info);
			} else {
				userInfo = null;
			}
			if (userInfo != null) {
				for (String scope : tokenContext.getAuthorizedScopes()) {
					List<String> authList = new ArrayList<>();
					List<String> auths = new ArrayList<>();
					for (GrantedAuthority authority : authorityList) {
						auths.add(authority.getAuthority());
						String[] auth = authority.getAuthority().split(scope + ".");
						if (authority.getAuthority().equals(scope)) {
							customClaims.put(scope, null);
						}
						if (auth.length > 1) {
							log.debug("scope:{}, auth:{}", scope, auth);
							authList.add(auth[1]);
						}
						if ("group".equals(authority.getAuthority())) {
							List<Group> groups = groupService.userGroup(userId);
							List<String> groupList = new ArrayList<>();
							for (Group group: groups) {
								groupList.add(group.getGroupName());
							}
							info.put("group", groupList);
						}
					}
					if (!authList.isEmpty()) {
						info.put(scope, authList);
					}
					if (customClaims.containsKey("permission")) {
						info.put("permission", auths.stream().distinct().collect(Collectors.toList()));
					}
					userInfo = new OidcUserInfo(info);
				}
				for (Field field : OidcInfo.class.getDeclaredFields()) {
					for (String claim: userInfo.getClaims().keySet()) {
						if (field.getName().equals(claim)) {
							addClaim(tokenContext, claim, userInfo);
						}
						if (customClaims.containsKey(claim)) {
							addClaim(tokenContext, claim, userInfo);
						}
					}
				}
			}
			Map<String, Object> thirdPartyClaims = extractClaims(tokenContext.getPrincipal());

			tokenContext.getClaims().claims(existingClaims -> {
				// Remove conflicting claims set by this authorization server
				existingClaims.keySet().forEach(thirdPartyClaims::remove);
				// Remove standard id_token claims that could cause problems with clients
				ID_TOKEN_CLAIMS.forEach(thirdPartyClaims::remove);

				// Add all other claims directly to id_token
				existingClaims.putAll(thirdPartyClaims);
			});
		}
	}

	private Map<String, Object> extractClaims(Authentication principal) {
		Map<String, Object> claims;
		if (principal.getPrincipal() instanceof OidcUser) {
			OidcUser oidcUser = (OidcUser) principal.getPrincipal();
			OidcIdToken idToken = oidcUser.getIdToken();
			claims = idToken.getClaims();
		} else if (principal.getPrincipal() instanceof OAuth2User) {
			OAuth2User oauth2User = (OAuth2User) principal.getPrincipal();
			claims = oauth2User.getAttributes();
		} else {
			claims = Collections.emptyMap();
		}
		return new HashMap<>(claims);
	}

	private void addClaim(JwtEncodingContext context, String claim, OidcUserInfo userInfo) {
		Object info =  userInfo.getClaims().get(claim);
		if (info != null && !info.toString().isBlank()) {
			context.getClaims().claims(claims -> claims.put(claim, info));
		}
	}

	private static String computeSHA256Thumbprint(X509Certificate x509Certificate) throws Exception {
		MessageDigest md = MessageDigest.getInstance("SHA-256");
		byte[] digest = md.digest(x509Certificate.getEncoded());
		return Base64.getUrlEncoder().withoutPadding().encodeToString(digest);
	}
}
