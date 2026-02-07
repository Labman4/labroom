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

package com.elpsykongroo.services.elasticsearch.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

@Data
@Component
@ConfigurationProperties(prefix = "service")
public class ServiceConfig {

    private TimeOut timeout;

    private String env;

    private String security;

    private String limit;

    private Elastic es;

    //public ca
    private SSL ssl;

    private OAuth2 oauth2;

    private String credentialsPath;

    private Vault vault;

    @Data
    public static class SSL {
        
        private String type;

        private String ca ;

        private String cert;

        private String key;
    }

    @Data
    public static class Elastic {

        private TimeOut timeout;
        //self ca
        private SSL ssl ;

        private String[] nodes;

        private String user;

        private String pass;

    }

    @Data
    public static class TimeOut {

        private Long connect;

        private Long socket;

        private Long read;

        private String storageUrl;

        private String storageLock;

        private String qrcodeToken;

        private String publicKey;
    }

    @Data
    public static class OAuth2 {
        private String clientId;

        private String clientSecret;

        private String tokenUri;

        private String registerId;

        private boolean enable;
    }

    @Data
    public static class Vault {

        private Refresh refresh;

        private boolean enable;
    }

    @Data
    public static class Refresh {

        private Long duration;

        private boolean enable;
    }

}