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

import com.elpsykongroo.gateway.interceptor.OAuth2Interceptor;
import com.elpsykongroo.gateway.service.RedisService;
import com.elpsykongroo.gateway.service.SearchService;
import com.elpsykongroo.gateway.service.MessageService;
import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import feign.Feign;
import feign.codec.Decoder;
import feign.codec.Encoder;
import feign.codec.StringDecoder;
import feign.jackson.JacksonEncoder;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.oauth2.client.OAuth2AuthorizedClientManager;

@Configuration(proxyBeanMethods = false)
public class FeignConfig {

    @Autowired
    private OAuth2AuthorizedClientManager clientManager;

    @Autowired
    private ServiceConfig serviceConfig;

    @Bean
    public MessageService messageService() {
        return Feign.builder()
                .decoder(new Decoder.Default())
                .encoder(new JacksonEncoder(new ObjectMapper().registerModule(new JavaTimeModule()).disable(SerializationFeature.FAIL_ON_EMPTY_BEANS)))
                .requestInterceptor(new OAuth2Interceptor(clientManager, serviceConfig))
                .target(MessageService.class, serviceConfig.getUrl().getMessage());
    }

    @Bean
    public RedisService redisService() {
        return Feign.builder()
                .decoder(new Decoder.Default())
                .encoder(new Encoder.Default())
                .target(RedisService.class, serviceConfig.getUrl().getRedis());
    }

    @Bean
    public SearchService searchService() {
        return Feign.builder()
                .decoder(new StringDecoder())
                .encoder(new JacksonEncoder(new ObjectMapper().registerModule(new JavaTimeModule()).disable(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES)))
                .target(SearchService.class, serviceConfig.getUrl().getEs());
    }

//    @Bean
//    public StorageService storageService() {
//        return Feign.builder()
//                .decoder(new StringDecoder())
//                .encoder(new SpringFormEncoder(new JacksonEncoder()))
//                .target(StorageService.class, serviceConfig.getUrl().getStorage());
//    }
}
