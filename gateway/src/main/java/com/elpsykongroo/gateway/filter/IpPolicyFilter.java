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

import com.elpsykongroo.base.domain.IpPolicy;
import com.elpsykongroo.base.domain.search.repo.IpManage;
import com.elpsykongroo.base.utils.IPUtils;
import com.elpsykongroo.base.utils.PathUtils;
import com.elpsykongroo.gateway.manager.IpPolicyManager;
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

    private String nonPrivate;

    public IpPolicyFilter (IpPolicyManager ipPolicyManager,
                           String headers,
                           String nonPrivate){
        this.ipPolicyManager = ipPolicyManager;
        this.headers = headers;
        this.nonPrivate = nonPrivate;
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain filterChain) throws ServletException, IOException {
        String ip = IPUtils.accessIP(request, headers);
        if (IPUtils.isPrivate(ip)) {
            filterChain.doFilter(request, response);
        }
        IpPolicy ipPolicy = ipPolicyManager.get();
        List<IpManage> black = ipPolicy.getBlack();
        if (!PathUtils.beginWithPath(nonPrivate,request.getRequestURI())) {
            List<IpManage> white = ipPolicy.getWhite();
            boolean flag = false;
            if(white != null && !white.isEmpty()) {
                for (IpManage ipManage : white) {
                    if (ipManage.getAddress().equals(ip)) {
                        flag = true;
                        break;
                    }
                }
                if (!flag) {
                    response.setStatus(HttpServletResponse.SC_FORBIDDEN);
                    response.setContentType("text/plain;charset=UTF-8");
                    response.getWriter().write("your ip is not allowed");
                    return;
                }
            }
        } else if (black != null && !black.isEmpty()) {
            for (IpManage ipManage : black) {
                if (ipManage.getAddress().equals(ip)) {
                    response.setStatus(HttpServletResponse.SC_FORBIDDEN);
                    response.setContentType("text/plain;charset=UTF-8");
                    response.getWriter().write("your ip is blocked");
                }
            }
        }
        filterChain.doFilter(request, response);
    }
}
