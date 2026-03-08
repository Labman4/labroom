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

package com.elpsykongroo.gateway.config;

import com.elpsykongroo.gateway.filter.RecordFilter;
import com.elpsykongroo.gateway.service.AccessRecordService;
import com.elpsykongroo.gateway.manager.DynamicConfigManager;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration(proxyBeanMethods = false)
@ConditionalOnProperty(
        prefix = "service",
        name = "record",
        havingValue = "true",
        matchIfMissing = true)
public class RecordConfig {

    @Autowired
    private RequestConfig requestConfig;

    @Autowired
    private DynamicConfigManager dynamicConfigManager;

    @Autowired
    private AccessRecordService accessRecordService;

    @Bean
    public RecordFilter recordFilter() {
        return new RecordFilter(requestConfig.getHeaders(),
                dynamicConfigManager,
                accessRecordService);
    }
}
