/*
 * Copyright 2020-2021 the original author or authors.
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
package com.oauth.sample.config;

import com.nimbusds.jose.jwk.JWKSet;
import com.nimbusds.jose.jwk.RSAKey;
import com.nimbusds.jose.jwk.source.JWKSource;
import com.nimbusds.jose.proc.SecurityContext;
import com.oauth.sample.jose.Jwks;
import com.oauth.sample.support.core.FormIdentityLoginConfigurer;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.security.config.Customizer;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.oauth2.core.AuthorizationGrantType;
import org.springframework.security.oauth2.core.ClientAuthenticationMethod;
import org.springframework.security.oauth2.core.oidc.OidcScopes;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.security.oauth2.server.authorization.client.InMemoryRegisteredClientRepository;
import org.springframework.security.oauth2.server.authorization.client.RegisteredClient;
import org.springframework.security.oauth2.server.authorization.client.RegisteredClientRepository;
import org.springframework.security.oauth2.server.authorization.config.annotation.web.configuration.OAuth2AuthorizationServerConfiguration;
import org.springframework.security.oauth2.server.authorization.config.annotation.web.configurers.OAuth2AuthorizationServerConfigurer;
import org.springframework.security.oauth2.server.authorization.settings.AuthorizationServerSettings;
import org.springframework.security.oauth2.server.authorization.web.authentication.DelegatingAuthenticationConverter;
import org.springframework.security.oauth2.server.authorization.web.authentication.OAuth2AuthorizationCodeRequestAuthenticationConverter;
import org.springframework.security.web.DefaultSecurityFilterChain;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.AuthenticationConverter;

import java.util.Arrays;
import java.util.Date;
import java.util.UUID;

/**
 * @author Joe Grandja
 * @since 0.0.1
 */
@Configuration(proxyBeanMethods = false)
public class AuthorizationServerConfig {


	@Bean
	@Order(Ordered.HIGHEST_PRECEDENCE)
	public SecurityFilterChain authorizationServerSecurityFilterChain(HttpSecurity http) throws Exception {

		// OAuth 2.1 默认配置
		// 缺省配置：authorizeRequests.anyRequest().authenticated()、
		// csrf.ignoringRequestMatchers(endpointsMatcher) 等等
		OAuth2AuthorizationServerConfiguration.applyDefaultSecurity(http);

		// 使用 HttpSecurity 获取 OAuth 2.1 配置中的 OAuth2AuthorizationServerConfigurer 对象
		OAuth2AuthorizationServerConfigurer authorizationServerConfigurer = http
				.getConfigurer(OAuth2AuthorizationServerConfigurer.class);

		authorizationServerConfigurer.tokenEndpoint((tokenEndpoint) -> {// 个性化认证授权端点
					tokenEndpoint.accessTokenRequestConverter(accessTokenRequestConverter()); // 注入自定义的授权认证Converter
							// 登录成功处理器
							// 登录失败处理器
				})// 处理客户端认证异常
				.authorizationEndpoint(authorizationEndpoint -> authorizationEndpoint// 授权码端点个性化confirm页面
						.consentPage("/token/confirm_access"));

		DefaultSecurityFilterChain securityFilterChain = authorizationServerConfigurer
//				.authorizationService(authorizationService)// redis存储token的实现
//				.authorizationServerSettings(
//						AuthorizationServerSettings.builder().issuer(SecurityConstants.PROJECT_LICENSE).build())
				// 授权码登录的登录页个性化
				.and()
				.apply(new FormIdentityLoginConfigurer())
				.and()
				.build();

		return securityFilterChain;

	}

	private AuthenticationConverter accessTokenRequestConverter() {
		return new DelegatingAuthenticationConverter(
				Arrays.asList(

						new OAuth2AuthorizationCodeRequestAuthenticationConverter()));
	}
	// @formatter:off
	@Bean
	public RegisteredClientRepository registeredClientRepository() {
		/**   {noop} 没有加密算法
		 *     bcrypt算法 {bcrypt}$2a$10$iMz8sMVMiOgRgXRuREF/f.ChT/rpu2ZtitfkT5CkDbZpZlFhLxO3y
		 *     MD5 算法：{jeHjgiJ4WuK/ChKvZZgXt3zcHGKw6SlfCCU4LBbRpwE=}a7b839a891dcfd1f3d146583cf7c8d1f
		 *     PDK 算法 ：{pbkdf2}cc409867e39f011f6332bbb6634f58e98d07be7fceefb4cc27e62501594d6ed0b271a25fd9f7fc2e
		 *
		 */
		RegisteredClient registeredClient = RegisteredClient.withId(UUID.randomUUID().toString())
				.clientId("messaging-client")
				.clientSecret("{noop}secret")
				// MD5 加密
//				.clientSecret("{MD5}{l3JoIK9EJ/UbQ2+L97B/L48kvdPKqtwhG2nGvgF5csM=}f6bc9636744e0ff0862316647872ae32") //密码是secret MD5加密
                .clientAuthenticationMethod(ClientAuthenticationMethod.CLIENT_SECRET_BASIC)
                .authorizationGrantType(AuthorizationGrantType.AUTHORIZATION_CODE)
                .authorizationGrantType(AuthorizationGrantType.REFRESH_TOKEN)
                .authorizationGrantType(AuthorizationGrantType.CLIENT_CREDENTIALS)
                .authorizationGrantType(AuthorizationGrantType.REFRESH_TOKEN)
				.authorizationGrantType(AuthorizationGrantType.PASSWORD)
				.authorizationGrantType(AuthorizationGrantType.IMPLICIT)
				.redirectUri("http://127.0.0.1:8080/login/oauth2/code/messaging-client-oidc")
				.redirectUri("http://127.0.0.1:8080/authorized")
				.scope(OidcScopes.OPENID)
				.scope("message.read")
				.scope("message.write")
				.clientSecretExpiresAt(new Date(System.currentTimeMillis() + 50000).toInstant())
				.build();
		return new InMemoryRegisteredClientRepository(registeredClient);
	}
	// @formatter:on

	@Bean
	public JWKSource<SecurityContext> jwkSource() {
		// 使用RAS加密接密
		RSAKey rsaKey = Jwks.generateRsa();
		JWKSet jwkSet = new JWKSet(rsaKey);
		return (jwkSelector, securityContext) -> jwkSelector.select(jwkSet);
	}

	@Bean
	public JwtDecoder jwtDecoder(JWKSource<SecurityContext> jwkSource) {
		return OAuth2AuthorizationServerConfiguration.jwtDecoder(jwkSource);
	}

	@Bean
	public AuthorizationServerSettings provider() {
		return AuthorizationServerSettings.builder()
				// jwt 签名发地址
				.issuer("http://localhost:8033")
				.build();
	}

}
