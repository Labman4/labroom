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

package com.elpsykongroo.base.config;

import com.elpsykongroo.base.domain.Limit;
import lombok.Data;

@Data
public class LimitConfig {
    public LimitConfig(Long globalTokens,
                       Long globalDuration,
                       Long globalSpeed,
                       Long tokens,
                       Long duration,
                       Long speed) {
        global.setTokens(globalTokens);
        global.setDuration(globalDuration);
        global.setSpeed(globalSpeed);
        scope.setTokens(tokens);
        scope.setDuration(duration);
        scope.setSpeed(speed);
    }

    private Limit global = new Limit();

    private Limit scope = new Limit();
}
