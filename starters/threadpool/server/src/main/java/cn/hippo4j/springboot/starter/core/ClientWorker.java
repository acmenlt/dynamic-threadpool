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

package cn.hippo4j.springboot.starter.core;

import cn.hippo4j.common.executor.ThreadFactoryBuilder;
import cn.hippo4j.common.model.Result;
import cn.hippo4j.common.model.ThreadPoolParameterInfo;
import cn.hippo4j.common.toolkit.ContentUtil;
import cn.hippo4j.common.toolkit.GroupKey;
import cn.hippo4j.common.toolkit.IdUtil;
import cn.hippo4j.common.toolkit.JSONUtil;
import cn.hippo4j.springboot.starter.remote.HttpAgent;
import cn.hippo4j.springboot.starter.remote.ServerHealthCheck;
import lombok.SneakyThrows;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.DisposableBean;
import org.springframework.util.StringUtils;

import java.net.URLDecoder;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

import static cn.hippo4j.common.constant.Constants.CONFIG_LONG_POLL_TIMEOUT;
import static cn.hippo4j.common.constant.Constants.GROUP_KEY_DELIMITER_TRANSLATION;
import static cn.hippo4j.common.constant.Constants.WORD_SEPARATOR;
import static cn.hippo4j.common.constant.Constants.LINE_SEPARATOR;
import static cn.hippo4j.common.constant.Constants.LISTENING_CONFIGS;
import static cn.hippo4j.common.constant.Constants.WEIGHT_CONFIGS;
import static cn.hippo4j.common.constant.Constants.LONG_PULLING_TIMEOUT;
import static cn.hippo4j.common.constant.Constants.LONG_PULLING_CLIENT_IDENTIFICATION;
import static cn.hippo4j.common.constant.Constants.LONG_PULLING_TIMEOUT_NO_HANGUP;
import static cn.hippo4j.common.constant.Constants.CLIENT_VERSION;
import static cn.hippo4j.common.constant.Constants.LISTENER_PATH;
import static cn.hippo4j.common.constant.Constants.INITIAL_CAPACITY;
import static cn.hippo4j.common.constant.Constants.DATA_GROUP_TENANT_SIZE;
import static cn.hippo4j.common.constant.Constants.CONFIG_CONTROLLER_PATH;
import static cn.hippo4j.common.constant.Constants.NULL;

/**
 * Client worker.
 */
@Slf4j
public class ClientWorker implements DisposableBean {

    private final long timeout;
    private final String identify;
    private final String version;

    private final HttpAgent agent;
    private final ServerHealthCheck serverHealthCheck;
    private final ScheduledExecutorService executorService;
    private final ClientShutdown hippo4jClientShutdown;

    private final CountDownLatch awaitApplicationComplete = new CountDownLatch(1);
    private final CountDownLatch cacheCondition = new CountDownLatch(1);
    private final ConcurrentHashMap<String, CacheData> cacheMap = new ConcurrentHashMap<>(16);

    private final long defaultTimedOut = 3000L;

    @SuppressWarnings("all")
    public ClientWorker(HttpAgent httpAgent,
                        String identify,
                        ServerHealthCheck serverHealthCheck,
                        String version,
                        ClientShutdown hippo4jClientShutdown) {
        this.agent = httpAgent;
        this.identify = identify;
        this.timeout = CONFIG_LONG_POLL_TIMEOUT;
        this.version = version;
        this.serverHealthCheck = serverHealthCheck;
        this.hippo4jClientShutdown = hippo4jClientShutdown;
        ScheduledExecutorService executor = Executors.newScheduledThreadPool(1, runnable -> {
            Thread thread = new Thread(runnable);
            thread.setName("client.worker.executor");
            thread.setDaemon(true);
            return thread;
        });
        this.executorService = Executors.newSingleThreadScheduledExecutor(
                ThreadFactoryBuilder.builder().prefix("client.long.polling.executor").daemon(true).build());
        log.info("Client identify: {}", identify);
        executor.schedule(() -> {
            try {
                awaitApplicationComplete.await();
                executorService.execute(new LongPollingRunnable(cacheMap.isEmpty(), cacheCondition));
            } catch (Throwable ex) {
                log.error("Sub check rotate check error.", ex);
            }
        }, 1L, TimeUnit.MILLISECONDS);
    }

    @Override
    public void destroy() throws Exception {
        executorService.shutdownNow();
    }

    /**
     * LongPollingRunnable
     */
    class LongPollingRunnable implements Runnable {

        private boolean cacheMapInitEmptyFlag;

        private final CountDownLatch cacheCondition;

        LongPollingRunnable(boolean cacheMapInitEmptyFlag, CountDownLatch cacheCondition) {
            this.cacheMapInitEmptyFlag = cacheMapInitEmptyFlag;
            this.cacheCondition = cacheCondition;
        }

        @Override
        @SneakyThrows
        public void run() {
            if (executorService.isShutdown() || hippo4jClientShutdown.isPrepareClose()) {
                hippo4jClientShutdown.countDown();
                log.info("The task of monitoring dynamic thread pool changes has stopped.");
                return;
            }
            if (cacheMapInitEmptyFlag) {
                cacheCondition.await();
                cacheMapInitEmptyFlag = false;
            }
            serverHealthCheck.isHealthStatus();
            List<CacheData> cacheDataList = new ArrayList<>();
            List<String> inInitializingCacheList = new ArrayList<>();
            cacheMap.forEach((key, val) -> cacheDataList.add(val));
            List<String> changedTpIds = checkUpdateDataIds(cacheDataList, inInitializingCacheList);
            for (String groupKey : changedTpIds) {
                String[] keys = groupKey.split(GROUP_KEY_DELIMITER_TRANSLATION);
                String tpId = keys[0];
                String itemId = keys[1];
                String namespace = keys[2];
                try {
                    String content = getServerConfig(namespace, itemId, tpId, defaultTimedOut);
                    CacheData cacheData = cacheMap.get(tpId);
                    String poolContent = ContentUtil.getPoolContent(JSONUtil.parseObject(content, ThreadPoolParameterInfo.class));
                    cacheData.setContent(poolContent);
                } catch (Exception ignored) {
                    log.error("Failed to get the latest thread pool configuration.", ignored);
                }
            }
            for (CacheData cacheData : cacheDataList) {
                if (!cacheData.isInitializing() || inInitializingCacheList
                        .contains(GroupKey.getKeyTenant(cacheData.getThreadPoolId(), cacheData.getItemId(), cacheData.getTenantId()))) {
                    cacheData.checkListenerMd5();
                    cacheData.setInitializing(false);
                }
            }
            inInitializingCacheList.clear();
            executorService.execute(this);
        }
    }

    private List<String> checkUpdateDataIds(List<CacheData> cacheDataList, List<String> inInitializingCacheList) {
        StringBuilder sb = new StringBuilder();
        for (CacheData cacheData : cacheDataList) {
            sb.append(cacheData.getThreadPoolId()).append(WORD_SEPARATOR);
            sb.append(cacheData.getItemId()).append(WORD_SEPARATOR);
            sb.append(cacheData.getTenantId()).append(WORD_SEPARATOR);
            sb.append(identify).append(WORD_SEPARATOR);
            sb.append(cacheData.getMd5()).append(LINE_SEPARATOR);
            if (cacheData.isInitializing()) {
                inInitializingCacheList.add(GroupKey.getKeyTenant(cacheData.getThreadPoolId(), cacheData.getItemId(), cacheData.getTenantId()));
            }
        }
        boolean isInitializingCacheList = !inInitializingCacheList.isEmpty();
        return checkUpdateTpIds(sb.toString(), isInitializingCacheList);
    }

    public List<String> checkUpdateTpIds(String probeUpdateString, boolean isInitializingCacheList) {
        if (StringUtils.isEmpty(probeUpdateString)) {
            return Collections.emptyList();
        }
        Map<String, String> params = new HashMap<>(2);
        params.put(LISTENING_CONFIGS, probeUpdateString);
        params.put(WEIGHT_CONFIGS, IdUtil.simpleUUID());
        Map<String, String> headers = new HashMap<>(2);
        headers.put(LONG_PULLING_TIMEOUT, "" + timeout);
        // Confirm the identity of the client, and can be modified separately when modifying the thread pool configuration.
        headers.put(LONG_PULLING_CLIENT_IDENTIFICATION, identify);
        // Told server do not hang me up if new initializing cacheData added in.
        if (isInitializingCacheList) {
            headers.put(LONG_PULLING_TIMEOUT_NO_HANGUP, "true");
        }
        headers.put(CLIENT_VERSION, version);
        try {
            long readTimeoutMs = timeout + Math.round(timeout >> 1);
            Result result = agent.httpPostByConfig(LISTENER_PATH, headers, params, readTimeoutMs);
            if (result != null && result.isSuccess()) {
                return parseUpdateDataIdResponse(result.getData().toString());
            }
        } catch (Exception ex) {
            setHealthServer(false);
            log.error("Check update get changed dataId exception. error message: {}", ex.getMessage());
        }
        return Collections.emptyList();
    }

    public String getServerConfig(String namespace, String itemId, String threadPoolId, long readTimeout) {
        Map<String, String> params = new HashMap<>(INITIAL_CAPACITY);
        params.put("namespace", namespace);
        params.put("itemId", itemId);
        params.put("tpId", threadPoolId);
        params.put("instanceId", identify);
        Result result = agent.httpGetByConfig(CONFIG_CONTROLLER_PATH, null, params, readTimeout);
        if (result.isSuccess()) {
            return JSONUtil.toJSONString(result.getData());
        }
        log.error("Sub server namespace: {}, itemId: {}, threadPoolId: {}, result code: {}", namespace, itemId, threadPoolId, result.getCode());
        return NULL;
    }

    public List<String> parseUpdateDataIdResponse(String response) {
        if (StringUtils.isEmpty(response)) {
            return Collections.emptyList();
        }
        try {
            response = URLDecoder.decode(response, "UTF-8");
        } catch (Exception e) {
            log.error("Polling resp decode modifiedDataIdsString error.", e);
        }
        List<String> updateList = new LinkedList<>();
        for (String dataIdAndGroup : response.split(LINE_SEPARATOR)) {
            if (!StringUtils.isEmpty(dataIdAndGroup)) {
                String[] keyArr = dataIdAndGroup.split(WORD_SEPARATOR);
                String dataId = keyArr[0];
                String group = keyArr[1];
                if (keyArr.length == DATA_GROUP_TENANT_SIZE) {
                    String tenant = keyArr[2];
                    updateList.add(GroupKey.getKeyTenant(dataId, group, tenant));
                    log.info("[{}] Refresh thread pool changed.", dataId);
                } else {
                    log.error("[{}] Polling resp invalid dataIdAndGroup error.", dataIdAndGroup);
                }
            }
        }
        return updateList;
    }

    public void addTenantListeners(String namespace, String itemId, String threadPoolId, List<? extends Listener> listeners) {
        CacheData cacheData = addCacheDataIfAbsent(namespace, itemId, threadPoolId);
        for (Listener listener : listeners) {
            cacheData.addListener(listener);
        }
        // Lazy loading
        if (awaitApplicationComplete.getCount() == 0L) {
            cacheCondition.countDown();
        }
    }

    public CacheData addCacheDataIfAbsent(String namespace, String itemId, String threadPoolId) {
        CacheData cacheData = cacheMap.get(threadPoolId);
        if (cacheData != null) {
            return cacheData;
        }
        return cacheMap.computeIfAbsent(threadPoolId, k-> {
            String serverConfig;
            try {
                serverConfig = getServerConfig(namespace, itemId, threadPoolId, defaultTimedOut);
                ThreadPoolParameterInfo poolInfo = JSONUtil.parseObject(serverConfig, ThreadPoolParameterInfo.class);
                cacheData.setContent(ContentUtil.getPoolContent(poolInfo));
            } catch (Exception ex) {
                log.error("Cache Data Error. Service Unavailable: {}", ex.getMessage());
            }
            return cacheData;
        });
    }

    private void setHealthServer(boolean isHealthServer) {
        this.serverHealthCheck.setHealthStatus(isHealthServer);
    }

    public void notifyApplicationComplete() {
        awaitApplicationComplete.countDown();
    }
}
