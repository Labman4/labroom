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

import com.elpsykongroo.auth.filter.IpPolicyFilter;
import com.elpsykongroo.auth.filter.RecordFilter;
import com.elpsykongroo.auth.manager.DynamicConfigManager;
import com.elpsykongroo.auth.manager.IpPolicyManager;
import com.elpsykongroo.auth.service.GatewayService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration(proxyBeanMethods = false)
public class CustomConfig {
    @Autowired
    private RequestConfig requestConfig;

    @Autowired
    private IpPolicyManager ipPolicyManager;


    @Autowired
    private DynamicConfigManager dynamicConfigManager;

    @Autowired
    private GatewayService gatewayService;

    @Bean
    public IpPolicyFilter ipPolicyFilter() {
        return new IpPolicyFilter(ipPolicyManager,
                requestConfig.getHeaders(),
                requestConfig.getPath().getNonPrivate());
    }

    @Bean
    public RecordFilter recordFilter() {
        return new RecordFilter(requestConfig.getHeaders(),
                dynamicConfigManager,
                gatewayService);
    }
}
