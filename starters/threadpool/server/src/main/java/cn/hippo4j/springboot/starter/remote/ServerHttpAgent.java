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

package cn.hippo4j.springboot.starter.remote;

import cn.hippo4j.common.model.Result;
import cn.hippo4j.core.config.ApplicationContextHolder;
import cn.hippo4j.common.constant.Constants;
import cn.hippo4j.common.executor.ThreadFactoryBuilder;
import cn.hippo4j.common.toolkit.StringUtil;
import cn.hippo4j.common.toolkit.http.HttpUtil;
import cn.hippo4j.springboot.starter.config.BootstrapProperties;
import cn.hippo4j.springboot.starter.security.SecurityProxy;

import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledThreadPoolExecutor;
import java.util.concurrent.TimeUnit;

/**
 * Server http agent.
 * 用来访问服务端的http代理类，ServerHttpAgent实现了HttpAgent接口
 *
 * 这个组件将会在DynamicThreadPoolAutoConfiguration 中被注入为组件
 */
public class ServerHttpAgent implements HttpAgent {

    //配置信息对象
    private final BootstrapProperties dynamicThreadPoolProperties;

    //服务地址管理器，这个对象中封装着可用的服务端地址列表，当然，服务端地址的列表信息，需要用户提前定义在配置文件中
    //nacos中也有这个类
    private final ServerListManager serverListManager;

    //安全代理类，如果有朋友自己看过nacos客户端源码，肯定对这个类的作用不陌生，这就是要给安全代理类
    //主要作用就是用来访问服务端的，通过这个类从服务端获得token，以后每次访问服务端都会携带这个token
    //但是在我迭代的nacos客户端代码中，我把这个功能给省略了，如果大家感兴趣可以自己去看看nacos客户端的源码
    private SecurityProxy securityProxy;

    private ServerHealthCheck serverHealthCheck;
    //定时任务执行器，这个定时任务执行器会定期刷新本地缓存的token
    private ScheduledExecutorService executorService;
    //定时任务执行间隔
    private final long securityInfoRefreshIntervalMills = TimeUnit.SECONDS.toMillis(5);

    public ServerHttpAgent(BootstrapProperties properties) {
        this.dynamicThreadPoolProperties = properties;
        this.serverListManager = new ServerListManager(dynamicThreadPoolProperties);
        this.securityProxy = new SecurityProxy(properties);
        //在这里已经先从服务端获取到了token了
        this.securityProxy.applyToken(this.serverListManager.getServerUrls());
        //创建定时任务执行器
        this.executorService = new ScheduledThreadPoolExecutor(
                new Integer(1),
                ThreadFactoryBuilder.builder().daemon(true).prefix("client.scheduled.token.security.updater").build());
        //向定时任务执行器提交了定期执行的任务
        this.executorService.scheduleWithFixedDelay(
                //定期访问服务端，刷新本地token
                () -> securityProxy.applyToken(serverListManager.getServerUrls()),
                0,
                securityInfoRefreshIntervalMills,
                TimeUnit.MILLISECONDS);
    }

    @Override
    public void start() {

    }

    @Override
    public String getTenantId() {
        return dynamicThreadPoolProperties.getNamespace();
    }

    @Override
    public String getEncode() {
        return null;
    }

    @Override
    public Result httpGetSimple(String path) {
        path = injectSecurityInfoByPath(path);
        return HttpUtil.get(buildUrl(path), Result.class);
    }

    //下面就是具体的访问服务端的方法了，这里只需要强调一点，那就是访问服务端之前
    //会调用本类的injectSecurityInfo方法，把本地token封装到请求中一起发送给服务端
    @Override
    public Result httpPost(String path, Object body) {
        isHealthStatus();
        path = injectSecurityInfoByPath(path);
        return HttpUtil.post(buildUrl(path), body, Result.class);
    }

    @Override
    public Result httpPostByDiscovery(String path, Object body) {
        isHealthStatus();
        path = injectSecurityInfoByPath(path);
        return HttpUtil.post(buildUrl(path), body, Result.class);
    }

    @Override
    public Result httpGetByConfig(String path, Map<String, String> headers, Map<String, String> paramValues, long readTimeoutMs) {
        isHealthStatus();
        injectSecurityInfo(paramValues);
        return HttpUtil.get(buildUrl(path), headers, paramValues, readTimeoutMs, Result.class);
    }

    @Override
    public Result httpPostByConfig(String path, Map<String, String> headers, Map<String, String> paramValues, long readTimeoutMs) {
        isHealthStatus();
        injectSecurityInfo(paramValues);
        return HttpUtil.post(buildUrl(path), headers, paramValues, readTimeoutMs, Result.class);
    }

    @Override
    public Result httpDeleteByConfig(String path, Map<String, String> headers, Map<String, String> paramValues, long readTimeoutMs) {
        return null;
    }

    private String buildUrl(String path) {
        return serverListManager.getCurrentServerAddr() + path;
    }

    private void isHealthStatus() {
        if (serverHealthCheck == null) {
            serverHealthCheck = ApplicationContextHolder.getBean(ServerHealthCheck.class);
        }
        serverHealthCheck.isHealthStatus();
    }

    //把本地token封装到map中的方法
    private Map injectSecurityInfo(Map<String, String> params) {
        if (StringUtil.isNotBlank(securityProxy.getAccessToken())) {
            params.put(Constants.ACCESS_TOKEN, securityProxy.getAccessToken());
        }
        return params;
    }

    @Deprecated
    private String injectSecurityInfoByPath(String path) {
        String resultPath = HttpUtil.buildUrl(path, injectSecurityInfo(new HashMap<>()));
        return resultPath;
    }
}
