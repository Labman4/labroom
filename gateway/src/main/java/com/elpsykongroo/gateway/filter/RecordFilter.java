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


package com.elpsykongroo.gateway.filter;

import com.elpsykongroo.base.domain.search.repo.AccessRecord;
import com.elpsykongroo.base.utils.IPUtils;
import com.elpsykongroo.base.utils.PathUtils;
import com.elpsykongroo.gateway.config.DynamicConfig;
import com.elpsykongroo.gateway.manager.DynamicConfigManager;
import com.elpsykongroo.gateway.service.AccessRecordService;
import io.micrometer.common.util.StringUtils;
import jakarta.servlet.Filter;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.ServletRequest;
import jakarta.servlet.ServletResponse;
import jakarta.servlet.http.HttpServletRequest;
import lombok.extern.slf4j.Slf4j;

import java.io.IOException;
import java.time.Instant;
import java.util.Enumeration;
import java.util.HashMap;
import java.util.Map;

@Slf4j
public class RecordFilter implements Filter {

    private String headers;

    private DynamicConfigManager dynamicConfigManager;

    private AccessRecordService accessRecordService;

    public RecordFilter(String headers,
                        DynamicConfigManager dynamicConfigManager,
                        AccessRecordService accessRecordService) {
        this.headers = headers;
        this.dynamicConfigManager = dynamicConfigManager;
        this.accessRecordService = accessRecordService;
    }

    @Override
    public void doFilter(ServletRequest servletRequest, ServletResponse servletResponse, FilterChain filterChain) throws IOException, ServletException {
        HttpServletRequest request = (HttpServletRequest) servletRequest;
        String ip = IPUtils.accessIP(request, headers);
        if (log.isDebugEnabled()) {
            log.debug("RecordUtils accessIp:{}", ip);
        }
        if(shouldRecord(ip,request.getRequestURI())){
            saveRecord(request, ip);
        }
        filterChain.doFilter(servletRequest,servletResponse);
    }

    public boolean shouldRecord(String ip, String path) {
        if (IPUtils.isPrivate(ip)) {
            return false;
        }
        DynamicConfig dynamicConfig = dynamicConfigManager.get();
        String excludeIp = dynamicConfig.getRecordExcludeIp();
        String excludePath = dynamicConfig.getRecordExcludePath();
        if (log.isDebugEnabled()) {
            log.debug("RecordUtils exclude ip:{}", excludeIp);
        }
        boolean recordFlag = IPUtils.filterByIpOrList(excludeIp, ip);
        if (!(StringUtils.isNotBlank(excludePath)
                && PathUtils.beginWithPath(excludePath, path))) {
            if (!recordFlag) {
                return true;
            }
        }
        return false;
    }

    public void saveRecord(HttpServletRequest request, String ip) {
        Map<String, String> result = new HashMap<>();
        Enumeration<String> headerNames = request.getHeaderNames();
        while (headerNames.hasMoreElements()) {
            String key = headerNames.nextElement();
            String value = request.getHeader(key);
            result.put(key, value);
        }
        AccessRecord record = new AccessRecord();
        record.setSourceIP(ip);
        record.setRequestHeader(result);
        record.setAccessPath(request.getRequestURI());
        record.setTimestamp(Instant.now().toString());
        record.setUserAgent(request.getHeader("user-agent"));
        try {
            accessRecordService.saveRecord(record);
            if (log.isDebugEnabled()) {
                log.debug("request header------------{} ", result);
            }
        } catch (Exception e) {
            if (log.isErrorEnabled()) {
                log.error("RecordUtils saveRecord error:{}", e.getMessage());
            }
        }
    }
}
