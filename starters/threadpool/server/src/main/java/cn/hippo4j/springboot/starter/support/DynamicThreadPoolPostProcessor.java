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

package cn.hippo4j.springboot.starter.support;

import cn.hippo4j.common.constant.Constants;
import cn.hippo4j.common.executor.ThreadPoolExecutorHolder;
import cn.hippo4j.common.executor.ThreadPoolExecutorRegistry;
import cn.hippo4j.common.executor.support.BlockingQueueTypeEnum;
import cn.hippo4j.common.executor.support.RejectedPolicyTypeEnum;
import cn.hippo4j.common.extension.enums.EnableEnum;
import cn.hippo4j.common.model.Result;
import cn.hippo4j.common.model.ThreadPoolParameterInfo;
import cn.hippo4j.common.model.register.DynamicThreadPoolRegisterParameter;
import cn.hippo4j.common.model.register.DynamicThreadPoolRegisterWrapper;
import cn.hippo4j.common.toolkit.BooleanUtil;
import cn.hippo4j.common.toolkit.JSONUtil;
import cn.hippo4j.common.toolkit.ReflectUtil;
import cn.hippo4j.common.toolkit.ThreadPoolExecutorUtil;
import cn.hippo4j.core.config.ApplicationContextHolder;
import cn.hippo4j.core.executor.DynamicThreadPool;
import cn.hippo4j.core.executor.DynamicThreadPoolExecutor;
import cn.hippo4j.core.executor.manage.GlobalThreadPoolManage;
import cn.hippo4j.core.executor.support.adpter.DynamicThreadPoolAdapterChoose;
import cn.hippo4j.core.toolkit.DynamicThreadPoolAnnotationUtil;
import cn.hippo4j.springboot.starter.config.BootstrapProperties;
import cn.hippo4j.springboot.starter.core.DynamicThreadPoolSubscribeConfig;
import cn.hippo4j.springboot.starter.remote.HttpAgent;
import cn.hippo4j.threadpool.message.core.service.GlobalNotifyAlarmManage;
import cn.hippo4j.threadpool.message.core.service.ThreadPoolNotifyAlarm;
import lombok.AllArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.BeansException;
import org.springframework.beans.factory.config.BeanPostProcessor;

import java.util.HashMap;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;

import static cn.hippo4j.common.constant.Constants.ACTIVE_ALARM;
import static cn.hippo4j.common.constant.Constants.CAPACITY_ALARM;
import static cn.hippo4j.common.constant.Constants.EXECUTE_TIME_OUT;
import static cn.hippo4j.common.constant.Constants.HTTP_EXECUTE_TIMEOUT;
import static cn.hippo4j.common.constant.Constants.INITIAL_CAPACITY;
import static cn.hippo4j.common.constant.Constants.ITEM_ID;
import static cn.hippo4j.common.constant.Constants.NAMESPACE;
import static cn.hippo4j.common.constant.Constants.TP_ID;

/**
 * Dynamic thread-pool post processor.
 * 这个组件在DynamicThreadPoolAutoConfiguration 中被配置为bean 组件
 */
@Slf4j
@AllArgsConstructor
public final class DynamicThreadPoolPostProcessor implements BeanPostProcessor {

    private final BootstrapProperties properties;
    private final HttpAgent httpAgent;
    private final DynamicThreadPoolSubscribeConfig dynamicThreadPoolSubscribeConfig;

    @Override
    public Object postProcessBeforeInitialization(Object bean, String beanName) {
        return bean;
    }

    @Override
    public Object postProcessAfterInitialization(Object bean, String beanName) throws BeansException {
        if (bean instanceof DynamicThreadPoolExecutor || DynamicThreadPoolAdapterChoose.match(bean)) {
            DynamicThreadPool dynamicThreadPool;
            try {
                dynamicThreadPool = ApplicationContextHolder.findAnnotationOnBean(beanName, DynamicThreadPool.class);
                if (Objects.isNull(dynamicThreadPool)) {
                    // Adapt to lower versions of SpringBoot.
                    dynamicThreadPool = DynamicThreadPoolAnnotationUtil.findAnnotationOnBean(beanName, DynamicThreadPool.class);
                    if (Objects.isNull(dynamicThreadPool)) {
                        return bean;
                    }
                }
            } catch (Exception ex) {
                log.error("Failed to create dynamic thread pool in annotation mode.", ex);
                return bean;
            }
            ThreadPoolExecutor dynamicThreadPoolExecutor = DynamicThreadPoolAdapterChoose.unwrap(bean);
            if ((dynamicThreadPoolExecutor) == null) {
                dynamicThreadPoolExecutor = (DynamicThreadPoolExecutor) bean;
            }
            //在这里把动态线程池的信息注册给服务端了
            ThreadPoolExecutor remoteThreadPoolExecutor = fillPoolAndRegister(((DynamicThreadPoolExecutor) dynamicThreadPoolExecutor).getThreadPoolId(), dynamicThreadPoolExecutor);
            DynamicThreadPoolAdapterChoose.replace(bean, remoteThreadPoolExecutor);
            subscribeConfig(((DynamicThreadPoolExecutor) dynamicThreadPoolExecutor).getThreadPoolId());
            return DynamicThreadPoolAdapterChoose.match(bean) ? bean : remoteThreadPoolExecutor;
        }
        return bean;
    }

    /**
     * Register and subscribe.
     *
     * @param threadPoolId dynamic thread-pool id
     * @param executor     dynamic thread-pool executor
     */
    protected void registerAndSubscribe(String threadPoolId, ThreadPoolExecutor executor) {
        fillPoolAndRegister(threadPoolId, executor);
        subscribeConfig(threadPoolId);
    }

    /**
     * Fill the thread pool and register.
     *
     * @param threadPoolId dynamic thread-pool id
     * @param executor     dynamic thread-pool executor
     * 注册线程池信息到服务端的方法，注意，这里交给fillPoolAndRegister方法的
     * 已经是dynamicThreadPoolWrapper对象了，而dynamicThreadPoolWrapper对象的代码之前已经展示过了
     *
     */
    protected ThreadPoolExecutor fillPoolAndRegister(String threadPoolId, ThreadPoolExecutor executor) {
        //封装线程池Id，命名空间，项目Id信息
        Map<String, String> queryStrMap = new HashMap<>(INITIAL_CAPACITY);
        queryStrMap.put(TP_ID, threadPoolId);
        queryStrMap.put(ITEM_ID, properties.getItemId());
        queryStrMap.put(NAMESPACE, properties.getNamespace());
        //创建封装线程池参数信息的对象
        ThreadPoolParameterInfo threadPoolParameterInfo = new ThreadPoolParameterInfo();
        //下面就是首先访问服务端，看看服务端是否存在动态线程池的配置信息的操作，如果存在就是用服务端的信息刷新本地动态线程池的配置信息
        try {
            //这里做了一个访问服务端的操作，这是因为也许用户通过web界面，已经实现在服务端定义好了线程池的配置信息
            //所以要以服务端的配置信息为主，因此在这里先访问服务端，看看服务端有没有设置好的动态线程池信息，其实就是去服务端查询数据库而已
            //这里访问的就是服务端的ConfigController类的detailConfigInfo方法
            //Constants.CONFIG_CONTROLLER_PATH就是要访问的服务端的接口，路径为"/hippo4j/v1/cs/configs"
            Result result = httpAgent.httpGetByConfig(Constants.CONFIG_CONTROLLER_PATH, null, queryStrMap, HTTP_EXECUTE_TIMEOUT);
            //判断返回的结果中是否存在最新的线程池配置信息
            if (result.isSuccess() && result.getData() != null) {
                String resultJsonStr = JSONUtil.toJSONString(result.getData());
                //如果存在就获取信息，然后转换成threadPoolParameterInfo对象
                threadPoolParameterInfo = JSONUtil.parseObject(resultJsonStr, ThreadPoolParameterInfo.class);
                if (threadPoolParameterInfo != null) {
                    //在这里刷新本地动态线程池的信息
                    threadPoolParamReplace(executor, threadPoolParameterInfo);
                    registerNotifyAlarm(threadPoolParameterInfo);
                }
            } else {
                // DynamicThreadPool configuration undefined in server
                //下面就是第一次把动态线程池注册到服务端的操作
                //如果走到这里就意味着服务端没有当前动态线程池的任何信息，那就要在客户端构建一个DynamicThreadPoolRegisterWrapper对象，然后把这个对象直接发送给服务端，进行注册即可
                //这里创建的这个DynamicThreadPoolRegisterParameter对象封装了动态线程池的核心参数信息
                DynamicThreadPoolRegisterParameter parameterInfo = DynamicThreadPoolRegisterParameter.builder()
                        .threadPoolId(threadPoolId)
                        .corePoolSize(executor.getCorePoolSize())
                        .maximumPoolSize(executor.getMaximumPoolSize())
                        .blockingQueueType(BlockingQueueTypeEnum.getBlockingQueueTypeEnumByName(executor.getQueue().getClass().getSimpleName()))
                        .capacity(executor.getQueue().remainingCapacity())
                        .threadFactory(executor.getThreadFactory())
                        .allowCoreThreadTimeOut(executor.allowsCoreThreadTimeOut())
                        .keepAliveTime(executor.getKeepAliveTime(TimeUnit.MILLISECONDS))
                        .isAlarm(false)
                        .activeAlarm(ACTIVE_ALARM)
                        .capacityAlarm(CAPACITY_ALARM)
                        .executeTimeOut(EXECUTE_TIME_OUT)
                        .rejectedPolicyType(RejectedPolicyTypeEnum.getRejectedPolicyTypeEnumByName(executor.getRejectedExecutionHandler().getClass().getSimpleName()))
                        .build();
                //在这里创建了DynamicThreadPoolRegisterWrapper对象，并且把刚才创建的parameterInfo交给registerWrapper对象，
                // 这个registerWrapper对象要发送给服务端进行注册
                DynamicThreadPoolRegisterWrapper registerWrapper = DynamicThreadPoolRegisterWrapper.builder()
                        .parameter(parameterInfo)
                        .build();
                //将线程池信息注册到服务端，这里是通过线程池全局管理器来注册的
                //还记得我之前在展示GlobalThreadPoolManage代码的时候，让大家对dynamicRegister方法混个眼熟，这里就用到了dynamicRegister方法
                //开始真正把客户端线程池信息注册到服务端了
                GlobalThreadPoolManage.dynamicRegister(registerWrapper);
            }
        } catch (Exception ex) {
            log.error("Failed to initialize thread pool configuration. error message: {}", ex.getMessage());
        }
        ThreadPoolExecutorHolder executorHolder = new ThreadPoolExecutorHolder(threadPoolId, executor, null);
        executorHolder.setParameterInfo(threadPoolParameterInfo);
        ThreadPoolExecutorRegistry.putHolder(executorHolder);
        return executor;
    }

    /**
     * Thread-pool param replace.
     *
     * @param executor                dynamic thread-pool executor
     * @param threadPoolParameterInfo thread-pool parameter info
     */
    private void threadPoolParamReplace(ThreadPoolExecutor executor, ThreadPoolParameterInfo threadPoolParameterInfo) {
        BlockingQueue workQueue = BlockingQueueTypeEnum.createBlockingQueue(threadPoolParameterInfo.getQueueType(), threadPoolParameterInfo.getCapacity());
        ReflectUtil.setFieldValue(executor, "workQueue", workQueue);
        // fix https://github.com/opengoofy/hippo4j/issues/1063
        ThreadPoolExecutorUtil.safeSetPoolSize(executor, threadPoolParameterInfo.corePoolSizeAdapt(), threadPoolParameterInfo.maximumPoolSizeAdapt());
        executor.setKeepAliveTime(threadPoolParameterInfo.getKeepAliveTime(), TimeUnit.SECONDS);
        executor.allowCoreThreadTimeOut(EnableEnum.getBool(threadPoolParameterInfo.getAllowCoreThreadTimeOut()));
        executor.setRejectedExecutionHandler(RejectedPolicyTypeEnum.createPolicy(threadPoolParameterInfo.getRejectedType()));
        if (executor instanceof DynamicThreadPoolExecutor) {
            Optional.ofNullable(threadPoolParameterInfo.getExecuteTimeOut())
                    .ifPresent(executeTimeOut -> ((DynamicThreadPoolExecutor) executor).setExecuteTimeOut(executeTimeOut));
        }
    }

    /**
     * Register notify alarm.
     *
     * @param threadPoolParameterInfo thread-pool parameter info
     */
    private void registerNotifyAlarm(ThreadPoolParameterInfo threadPoolParameterInfo) {
        // Set dynamic thread pool enhancement parameters.
        ThreadPoolNotifyAlarm threadPoolNotifyAlarm = new ThreadPoolNotifyAlarm(
                BooleanUtil.toBoolean(threadPoolParameterInfo.getIsAlarm().toString()),
                threadPoolParameterInfo.getLivenessAlarm(),
                threadPoolParameterInfo.getCapacityAlarm());
        GlobalNotifyAlarmManage.put(threadPoolParameterInfo.getTpId(), threadPoolNotifyAlarm);
    }

    /**
     * Client dynamic thread pool subscription server configuration.
     *
     * @param threadPoolId thread-pool id
     */
    protected void subscribeConfig(String threadPoolId) {
        dynamicThreadPoolSubscribeConfig.subscribeConfig(threadPoolId);
    }
}
