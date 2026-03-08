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

package com.elpsykongroo.gateway.service.impl;

import java.net.InetAddress;
import java.net.UnknownHostException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import com.elpsykongroo.base.domain.search.QueryParam;
import com.elpsykongroo.base.utils.IPUtils;
import com.elpsykongroo.base.domain.search.repo.AccessRecord;
import com.elpsykongroo.gateway.service.SearchService;

import com.elpsykongroo.gateway.service.AccessRecordService;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

@Service
@Slf4j
public class AccessRecordServiceImpl implements AccessRecordService {

	@Autowired
	private SearchService searchService;

	@Override
	public void saveRecord(AccessRecord record) {
		try {
			QueryParam queryParam = new QueryParam();
			queryParam.setIndex("access_record");
			queryParam.setOperation("save");
			queryParam.setEntity(record);
			searchService.query(queryParam);
		} catch (Exception e) {
			if (log.isErrorEnabled()) {
				log.error("saveRecord error: {}", e.getMessage());
			}
		}
	}

	@Override
	public String findAll(String timestamp, String id, String order, String scrollId) {
		QueryParam queryParam = new QueryParam();
		queryParam.setOrder(order);
		queryParam.setOrderBy("timestamp");
		queryParam.setTimestamp(timestamp);
		queryParam.setId(id);
		queryParam.setIndex("access_record");
		queryParam.setType(AccessRecord.class);
		queryParam.setScrollId(scrollId);
		return searchService.query(queryParam);
	}

	@Override
	public String deleteRecord(List<String> params) throws UnknownHostException {
		if (params.isEmpty()) {
			return "0";
		}
		QueryParam deleteParam = new QueryParam();
		deleteParam.setOperation("deleteQuery");
		deleteParam.setIndex("access_record");
		for (String param : params) {
			if (IPUtils.validateHost(param) || IPUtils.validate(param)) {
				InetAddress[] inetAddresses = InetAddress.getAllByName(param);
				deleteParam.setType(AccessRecord.class);
				deleteParam.setFields(Collections.singletonList("sourceIP"));
				deleteParam.setBoolQuery(true);
				if (param.contains("::") && IPUtils.isIpv6(param)) {
					deleteParam.setQueryStringParam(Collections.singletonList("\"" + param + "\""));
				} else {
					for (InetAddress addr : inetAddresses) {
						if (IPUtils.isIpv6(addr.getHostAddress())) {
							deleteParam.setQueryStringParam(Collections.singletonList("\"" + addr.getHostAddress() + "\""));
						} else {
							deleteParam.setQueryStringParam(Collections.singletonList(addr.getHostAddress()));
						}
					}
				}
			}
		}
		return searchService.query(deleteParam);
	}

	@Override
	public String filterByParams(String params, String timestamp, String id, String order, String scrollId){
		if (StringUtils.isNotBlank(params)) {
			List<String> fields = new ArrayList<>();
			fields.add("sourceIP");
			fields.add("userAgent");
			fields.add("accessPath");
			fields.add("requestHeader");
			QueryParam queryParam = new QueryParam();
			queryParam.setOrder(order);
			queryParam.setOrderBy("timestamp");
			queryParam.setTimestamp(timestamp);
			queryParam.setId(id);
			queryParam.setIndex("access_record");
			queryParam.setType(AccessRecord.class);
			queryParam.setParam(params);
			queryParam.setFields(fields);
			queryParam.setFuzzy(true);
			queryParam.setScrollId(scrollId);
			return searchService.query(queryParam);
		} else {
			return findAll(timestamp, id, order, "1");
		}
	}
}
