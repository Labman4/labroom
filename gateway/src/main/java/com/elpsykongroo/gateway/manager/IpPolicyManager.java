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

package com.elpsykongroo.gateway.manager;

import com.elpsykongroo.base.domain.search.repo.IpManage;
import com.elpsykongroo.base.utils.JsonUtils;
import com.elpsykongroo.base.domain.IpPolicy;
import com.elpsykongroo.gateway.service.IPManagerService;
import com.elpsykongroo.gateway.service.RedisService;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.EnableScheduling;
import org.springframework.scheduling.annotation.Scheduled;

import java.util.List;
import java.util.concurrent.atomic.AtomicReference;

@Slf4j
@EnableScheduling
@Configuration(proxyBeanMethods = false)
@ConditionalOnProperty(
        prefix = "service.limit.ip",
        name = "enable",
        havingValue = "true",
        matchIfMissing = true)
public class IpPolicyManager {

    private final AtomicReference<IpPolicy> ref = new AtomicReference<>();

    @Autowired
    private RedisService redisService;

    @Value("${ENV:dev}")
    private String env;

    @Value("${service.limit.ip.duration.white:10}")
    private String whiteCacheTime;

    @Value("${service.limit.ip.duration.black:30}")
    private String blackCacheTime;

    @Autowired
    private IPManagerService ipManagerService;

    private final ObjectMapper objectMapper = new ObjectMapper();

    @Scheduled(fixedDelayString = "${service.limit.ip.duration.refresh:600000}")
    private void updateIpPolicy() {
        IpPolicy ipPolicy = new IpPolicy();
        String blackList = updateIpList(true);
        String whiteList= updateIpList(false);
        if (StringUtils.isNotBlank(blackList)) {
            JsonNode jsonNode = JsonUtils.toJsonNode(blackList);
            JsonNode hits = jsonNode.get("hits");
            if(hits != null && !hits.isEmpty()){
                ipPolicy.setBlack(objectMapper.convertValue(hits, new TypeReference<List<IpManage>>() {}));
            }
        }
        if (StringUtils.isNotBlank(whiteList)) {
            JsonNode jsonNode = JsonUtils.toJsonNode(whiteList);
            JsonNode hits = jsonNode.get("hits");
            if(hits != null && !hits.isEmpty()){
                ipPolicy.setWhite(objectMapper.convertValue(hits, new TypeReference<List<IpManage>>() {}));
            }
        }
        ref.set(ipPolicy);
    }

    private String updateIpList(boolean black) {
        String list = "";
            if (black) {
                list = redisService.get("black@" + env);
            } else {
                list = redisService.get("white@" + env);
            }

            if (StringUtils.isBlank(list)) {
                list = ipManagerService.list(Boolean.toString(black), "asc");
                if (StringUtils.isNotBlank(list)) {
                    if (black) {
                        redisService.set("black@" + env, list, blackCacheTime);
                    } else {
                        redisService.set("white@" + env, list, whiteCacheTime);
                    }
                }
            }
        return list;
    }

    public IpPolicy get() {
        return ref.get();
    }
}