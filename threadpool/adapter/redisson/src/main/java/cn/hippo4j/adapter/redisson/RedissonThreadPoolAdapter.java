/*
 * Licensed to the Apache Software Foundation (ASF) under one or more
 * contributor license agreements.  See the NOTICE file distributed with
 * this work for additional information regarding copyright ownership.
 * The ASF licenses this file to You under the Apache License, Version 2.0
 * (the "License"); you may not use this file except in compliance with
 * the License.  You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package cn.hippo4j.adapter.redisson;

import cn.hippo4j.adapter.base.ThreadPoolAdapter;
import cn.hippo4j.adapter.base.ThreadPoolAdapterParameter;
import cn.hippo4j.common.model.ThreadPoolAdapterState;
import cn.hippo4j.common.toolkit.ThreadPoolExecutorUtil;
import cn.hippo4j.core.config.ApplicationContextHolder;
import lombok.extern.slf4j.Slf4j;
import org.redisson.Redisson;
import org.redisson.api.RedissonClient;
import org.springframework.boot.context.event.ApplicationStartedEvent;
import org.springframework.context.ApplicationListener;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ThreadPoolExecutor;

import static cn.hippo4j.common.constant.ChangeThreadPoolConstants.CHANGE_DELIMITER;

/**
 * Redisson thread-pool adapter.
 */
@Slf4j
public class RedissonThreadPoolAdapter implements ThreadPoolAdapter, ApplicationListener<ApplicationStartedEvent> {

    private final Map<String, ThreadPoolExecutor> redissonExecutors = new HashMap<>();

    @Override
    public String mark() {
        return "redisson";
    }

    @Override
    public ThreadPoolAdapterState getThreadPoolState(String identify) {
        ThreadPoolAdapterState result = new ThreadPoolAdapterState();
        ThreadPoolExecutor redissonExecutor = redissonExecutors.get(identify);
        if (redissonExecutor != null) {
            result.setThreadPoolKey(identify);
            result.setCoreSize(redissonExecutor.getCorePoolSize());
            result.setMaximumSize(redissonExecutor.getMaximumPoolSize());
            return result;
        }
        log.warn("[{}] Redisson thread pool not found.", identify);
        return result;
    }

    @Override
    public List<ThreadPoolAdapterState> getThreadPoolStates() {
        List<ThreadPoolAdapterState> adapterStateList = new ArrayList<>();
        redissonExecutors.forEach(
                (key, val) -> adapterStateList.add(getThreadPoolState(key)));
        return adapterStateList;
    }

    @Override
    public boolean updateThreadPool(ThreadPoolAdapterParameter threadPoolAdapterParameter) {
        String threadPoolKey = threadPoolAdapterParameter.getThreadPoolKey();
        ThreadPoolExecutor redissonExecutor = redissonExecutors.get(threadPoolKey);
        if (redissonExecutor != null) {
            int originalCoreSize = redissonExecutor.getCorePoolSize();
            int originalMaximumPoolSize = redissonExecutor.getMaximumPoolSize();
            ThreadPoolExecutorUtil.safeSetPoolSize(redissonExecutor, threadPoolAdapterParameter.getCorePoolSize(), threadPoolAdapterParameter.getMaximumPoolSize());
            log.info("[{}] Redisson thread pool parameter change. coreSize: {}, maximumSize: {}",
                    threadPoolKey,
                    String.format(CHANGE_DELIMITER, originalCoreSize, redissonExecutor.getCorePoolSize()),
                    String.format(CHANGE_DELIMITER, originalMaximumPoolSize, redissonExecutor.getMaximumPoolSize()));
            return true;
        }
        log.warn("[{}] Redisson thread pool not found.", threadPoolKey);
        return false;
    }

    @Override
    public void onApplicationEvent(ApplicationStartedEvent applicationStartedEvent) {
        Map<String, RedissonClient> redissonClientMap = ApplicationContextHolder.getBeansOfType(RedissonClient.class);
        try {
            redissonClientMap.forEach((name, redissonClient) -> {
                ThreadPoolExecutor threadPoolExecutor = (ThreadPoolExecutor) ((Redisson) redissonClient).getServiceManager().getExecutor();
                redissonExecutors.put(name, threadPoolExecutor);
            });
        } catch (Exception e) {
            log.error("Failed to get Redisson thread pool.", e);
        }
    }
}
