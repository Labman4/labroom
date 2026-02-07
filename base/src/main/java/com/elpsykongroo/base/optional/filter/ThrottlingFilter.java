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

package com.elpsykongroo.base.optional.filter;

import com.elpsykongroo.base.config.LimitConfig;
import io.github.bucket4j.Bandwidth;
import io.github.bucket4j.Bucket;
import io.github.bucket4j.BucketConfiguration;
import io.github.bucket4j.ConsumptionProbe;
import io.github.bucket4j.Refill;
import io.github.bucket4j.TimeMeter;
import io.github.bucket4j.distributed.jdbc.SQLProxyConfiguration;
import io.github.bucket4j.distributed.jdbc.SQLProxyConfigurationBuilder;
import io.github.bucket4j.distributed.proxy.ClientSideConfig;
import io.github.bucket4j.postgresql.PostgreSQLadvisoryLockBasedProxyManager;
import jakarta.servlet.Filter;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.ServletRequest;
import jakarta.servlet.ServletResponse;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.servlet.http.HttpSession;

import javax.sql.DataSource;
import java.io.IOException;
import java.time.Duration;
import java.util.concurrent.TimeUnit;

public class ThrottlingFilter implements Filter {

	private static final long GLOBAL_KEY = 1L;

    private final Bucket globalBucket;

	private final LimitConfig limitConfig;

	private DataSource dataSource;

	public ThrottlingFilter(LimitConfig limitConfig) {
		this.limitConfig = limitConfig;
		Bandwidth globalLimit = Bandwidth.classic(
				limitConfig.getGlobal().getTokens(),
				Refill.greedy(
						limitConfig.getGlobal().getSpeed(),
						Duration.ofSeconds(limitConfig.getGlobal().getDuration())
				)
		);
		this.globalBucket = Bucket.builder().addLimit(globalLimit).build();
	}

	public ThrottlingFilter(LimitConfig limitConfig, DataSource source) {
		this.limitConfig = limitConfig;
		this.dataSource = source;
        SQLProxyConfiguration configuration = SQLProxyConfigurationBuilder.builder()
				.withClientSideConfig(ClientSideConfig.getDefault().withClientClock(TimeMeter.SYSTEM_MILLISECONDS))
				.build(source);
        PostgreSQLadvisoryLockBasedProxyManager proxyManager = new PostgreSQLadvisoryLockBasedProxyManager(configuration);
		Bandwidth globalLimit = Bandwidth.classic(
				limitConfig.getGlobal().getTokens(),
				Refill.greedy(
						limitConfig.getGlobal().getSpeed(),
						Duration.ofSeconds(limitConfig.getGlobal().getDuration())
				)
		);
		BucketConfiguration globalConfig =
				BucketConfiguration.builder()
						.addLimit(globalLimit)
						.build();
		this.globalBucket =
				proxyManager.builder().build(GLOBAL_KEY, globalConfig);
	}

	@Override
	public void doFilter(ServletRequest servletRequest, ServletResponse servletResponse, FilterChain filterChain) throws IOException, ServletException {
		HttpServletRequest request = (HttpServletRequest) servletRequest;
		HttpServletResponse response = (HttpServletResponse) servletResponse;
		HttpSession session = request.getSession(true);
		ConsumptionProbe globalProbe = globalBucket.tryConsumeAndReturnRemaining(1);
		if (!globalProbe.isConsumed()) {
			reject(response, globalProbe, "Global rate limit exceeded");
			return;
		}
		if (dataSource == null) {
			Bucket sessionBucket = getSessionBucket(session);
			ConsumptionProbe sessionProbe = sessionBucket.tryConsumeAndReturnRemaining(1);
			if (!sessionProbe.isConsumed()) {
				reject(response, sessionProbe, "Session rate limit exceeded");
				return;
			}
		}
		filterChain.doFilter(servletRequest, servletResponse);
	}

	private Bucket getSessionBucket(HttpSession session) {
		String attrName = "session-throttler";
		Bucket bucket = (Bucket) session.getAttribute(attrName);
		if (bucket != null) {
			return bucket;
		}
		synchronized (session) {
			Bandwidth sessionLimit = Bandwidth.classic(
					limitConfig.getScope().getTokens(),
					Refill.greedy(
							limitConfig.getGlobal().getSpeed(),
							Duration.ofSeconds(limitConfig.getGlobal().getDuration())
					)
			);
			bucket = Bucket.builder().addLimit(sessionLimit).build();
			session.setAttribute(attrName, bucket);
			return bucket;
		}
	}

	private void reject(
			HttpServletResponse response,
			ConsumptionProbe probe,
			String message
	) throws IOException {

		response.setStatus(429);
		response.setContentType("text/plain");
		response.setHeader(
				"Retry-After",
				String.valueOf(
						TimeUnit.NANOSECONDS.toSeconds(
								probe.getNanosToWaitForRefill()
						)
				)
		);
		response.getWriter().write(message);
	}
}
