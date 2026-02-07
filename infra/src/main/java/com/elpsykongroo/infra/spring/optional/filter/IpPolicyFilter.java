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

package com.elpsykongroo.infra.spring.optional.filter;

import com.elpsykongroo.base.domain.search.repo.IpManage;
import com.elpsykongroo.base.utils.IPUtils;
import com.elpsykongroo.infra.spring.domain.IpPolicy;
import com.elpsykongroo.infra.spring.optional.manager.IpPolicyManager;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.util.List;

public class IpPolicyFilter extends OncePerRequestFilter {

    private IpPolicyManager ipPolicyManager;

    private String headers;

    public IpPolicyFilter (IpPolicyManager ipPolicyManager, String headers){
        this.ipPolicyManager = ipPolicyManager;
        this.headers = headers;
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain filterChain) throws ServletException, IOException {
        IpPolicy ipPolicy = ipPolicyManager.get();
        String ip = IPUtils.accessIP(request, headers);
        List<IpManage> black = ipPolicy.getBlack();
        if (black != null && !black.isEmpty()) {
            for (IpManage ipManage : black) {
                if (ipManage.getAddress().equals(ip)) {
                    response.setStatus(HttpServletResponse.SC_FORBIDDEN);
                    response.setContentType("text/plain;charset=UTF-8");
                    response.getWriter().write("your ip is blocked");
                    return;
                }
            }
        }
        List<IpManage> white = ipPolicy.getWhite();
        if(white != null && !white.isEmpty()) {
            boolean whiteHit = false;
            for (IpManage ipManage : ipPolicy.getWhite()) {
                if (ipManage.getAddress().equals(ip)) {
                    whiteHit = true;
                    break;
                }
            }
            if (!whiteHit) {
                response.setStatus(HttpServletResponse.SC_FORBIDDEN);
                response.setContentType("text/plain;charset=UTF-8");
                response.getWriter().write("your ip is not allowed");
                return;
            }
        }

        filterChain.doFilter(request, response);
    }
}
