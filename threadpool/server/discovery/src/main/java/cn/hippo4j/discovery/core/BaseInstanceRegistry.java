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

package cn.hippo4j.discovery.core;

import cn.hippo4j.common.executor.ThreadFactoryBuilder;
import cn.hippo4j.common.extension.design.AbstractSubjectCenter;
import cn.hippo4j.common.model.InstanceInfo;
import cn.hippo4j.common.model.InstanceInfo.InstanceStatus;
import cn.hippo4j.common.toolkit.CollectionUtil;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.util.CollectionUtils;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.TimerTask;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledThreadPoolExecutor;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.atomic.AtomicReference;

import static cn.hippo4j.common.constant.Constants.EVICTION_INTERVAL_TIMER_IN_MS;
import static cn.hippo4j.common.constant.Constants.SCHEDULED_THREAD_CORE_NUM;

/**
 * Base instance registry.
 *
 * <p> Reference from Eureka. Service registration, service offline, service renewal.
 */
@Slf4j
@Service
public class BaseInstanceRegistry implements InstanceRegistry<InstanceInfo> {

    private static final int CONTAINER_SIZE = 1024;

    private final ConcurrentHashMap<String, Map<String, Lease<InstanceInfo>>> registry = new ConcurrentHashMap<>(CONTAINER_SIZE);

    @Override
    public List<Lease<InstanceInfo>> listInstance(String appName) {
        Map<String, Lease<InstanceInfo>> appNameLeaseMap = registry.get(appName);
        return CollectionUtils.isEmpty(appNameLeaseMap) ? Collections.emptyList() : new ArrayList<>(appNameLeaseMap.values());
    }

    @Override
    public void register(InstanceInfo registrant) {
        Map<String, Lease<InstanceInfo>> registerMap = registry.get(registrant.getAppName());
        if (registerMap == null) {
            registerMap = registry.computeIfAbsent(registrant.getAppName(), k -> {
                ConcurrentHashMap<String, Lease<InstanceInfo>> registerNewMap = new ConcurrentHashMap<>();
                return registerNewMap;
            });
        }
        Lease<InstanceInfo> existingLease = registerMap.get(registrant.getInstanceId());
        if (existingLease != null && (existingLease.getHolder() != null)) {
            Long existingLastDirtyTimestamp = existingLease.getHolder().getLastDirtyTimestamp();
            Long registrationLastDirtyTimestamp = registrant.getLastDirtyTimestamp();
            if (existingLastDirtyTimestamp > registrationLastDirtyTimestamp) {
                registrant = existingLease.getHolder();
            }
        }
        Lease<InstanceInfo> lease = new Lease<>(registrant);
        if (existingLease != null) {
            lease.setServiceUpTimestamp(existingLease.getServiceUpTimestamp());
        }
        registerMap.put(registrant.getInstanceId(), lease);
        if (InstanceStatus.UP.equals(registrant.getStatus())) {
            lease.serviceUp();
        }
        registrant.setActionType(InstanceInfo.ActionType.ADDED);
        registrant.setLastUpdatedTimestamp();
    }

    @Override
    public boolean renew(InstanceInfo.InstanceRenew instanceRenew) {
        String appName = instanceRenew.getAppName();
        String instanceId = instanceRenew.getInstanceId();
        Map<String, Lease<InstanceInfo>> registryMap = registry.get(appName);
        if (registryMap == null) {
            return false;
        }
        Lease<InstanceInfo> leaseToRenew = registryMap.get(instanceId);
        if (leaseToRenew == null) {
            return false;
        }
        leaseToRenew.renew();
        return true;
    }

    @Override
    public void remove(InstanceInfo info) {
        String appName = info.getAppName();
        String instanceId = info.getInstanceId();
        Map<String, Lease<InstanceInfo>> leaseMap = registry.get(appName);
        if (CollectionUtil.isNotEmpty(leaseMap)) {
            Lease<InstanceInfo> remove = leaseMap.remove(instanceId);
            if (remove != null) {
                log.info("Remove unhealthy node, node ID: {}", instanceId);
            } else {
                log.warn("Failed to remove unhealthy node, no instance found: {}", instanceId);
            }
        } else {
            log.warn("Failed to remove unhealthy node, no application found: {}", appName);
        }
    }

    public void evict(long additionalLeaseMs) {
        List<Lease<InstanceInfo>> expiredLeases = new ArrayList<>();
        for (Map.Entry<String, Map<String, Lease<InstanceInfo>>> groupEntry : registry.entrySet()) {
            Map<String, Lease<InstanceInfo>> leaseMap = groupEntry.getValue();
            if (leaseMap != null) {
                for (Map.Entry<String, Lease<InstanceInfo>> leaseEntry : leaseMap.entrySet()) {
                    Lease<InstanceInfo> lease = leaseEntry.getValue();
                    if (lease.isExpired(additionalLeaseMs) && lease.getHolder() != null) {
                        expiredLeases.add(lease);
                    }
                }
            }
        }
        for (Lease<InstanceInfo> expiredLease : expiredLeases) {
            String appName = expiredLease.getHolder().getAppName();
            String id = expiredLease.getHolder().getInstanceId();
            String identify = expiredLease.getHolder().getIdentify();
            internalCancel(appName, id, identify);
        }
    }

    protected boolean internalCancel(String appName, String id, String identify) {
        Map<String, Lease<InstanceInfo>> registerMap = registry.get(appName);
        if (CollectionUtil.isNotEmpty(registerMap)) {
            registerMap.remove(id);
            AbstractSubjectCenter.notify(AbstractSubjectCenter.SubjectType.CLEAR_CONFIG_CACHE, () -> identify);
            log.info("Clean up unhealthy nodes. Node id: {}", id);
        }
        return true;
    }

    /**
     * EvictionTask
     */
    public class EvictionTask extends TimerTask {

        private final AtomicLong lastExecutionNanosRef = new AtomicLong(0L);

        @Override
        public void run() {
            try {
                long compensationTimeMs = getCompensationTimeMs();
                log.info("Running the evict task with compensationTime {} ms", compensationTimeMs);
                evict(compensationTimeMs);
            } catch (Throwable e) {
                log.error("Could not run the evict task", e);
            }
        }

        long getCompensationTimeMs() {
            long currNanos = getCurrentTimeNano();
            long lastNanos = lastExecutionNanosRef.getAndSet(currNanos);
            if (lastNanos == 0L) {
                return 0L;
            }
            long elapsedMs = TimeUnit.NANOSECONDS.toMillis(currNanos - lastNanos);
            long compensationTime = elapsedMs - EVICTION_INTERVAL_TIMER_IN_MS;
            return Math.max(compensationTime, 0L);
        }

        long getCurrentTimeNano() {
            return System.nanoTime();
        }
    }

    private final ScheduledExecutorService scheduledExecutorService =
            new ScheduledThreadPoolExecutor(
                    SCHEDULED_THREAD_CORE_NUM,
                    new ThreadFactoryBuilder()
                            .prefix("registry-eviction")
                            .daemon(true)
                            .build());

    private final AtomicReference<EvictionTask> evictionTaskRef = new AtomicReference<>();

    public void postInit() {
        evictionTaskRef.set(new BaseInstanceRegistry.EvictionTask());
        scheduledExecutorService.scheduleWithFixedDelay(evictionTaskRef.get(),
                EVICTION_INTERVAL_TIMER_IN_MS, EVICTION_INTERVAL_TIMER_IN_MS, TimeUnit.MILLISECONDS);
    }
}
