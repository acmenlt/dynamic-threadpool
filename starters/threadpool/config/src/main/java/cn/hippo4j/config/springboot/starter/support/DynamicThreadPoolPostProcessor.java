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

package cn.hippo4j.config.springboot.starter.support;

import cn.hippo4j.common.constant.Constants;
import cn.hippo4j.common.executor.ThreadPoolExecutorRegistry;
import cn.hippo4j.common.executor.support.BlockingQueueTypeEnum;
import cn.hippo4j.common.executor.support.RejectedPolicyTypeEnum;
import cn.hippo4j.common.model.executor.ExecutorNotifyProperties;
import cn.hippo4j.common.model.executor.ExecutorProperties;
import cn.hippo4j.common.toolkit.ReflectUtil;
import cn.hippo4j.common.toolkit.StringUtil;
import cn.hippo4j.common.toolkit.ThreadPoolExecutorUtil;
import cn.hippo4j.core.config.ApplicationContextHolder;
import cn.hippo4j.core.executor.DynamicThreadPool;
import cn.hippo4j.core.executor.DynamicThreadPoolExecutor;
import cn.hippo4j.core.executor.support.adpter.DynamicThreadPoolAdapterChoose;
import cn.hippo4j.core.toolkit.DynamicThreadPoolAnnotationUtil;
import cn.hippo4j.threadpool.dynamic.mode.config.properties.BootstrapConfigProperties;
import cn.hippo4j.threadpool.message.core.service.GlobalNotifyAlarmManage;
import cn.hippo4j.threadpool.message.core.service.ThreadPoolNotifyAlarm;
import lombok.AllArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.BeansException;
import org.springframework.beans.factory.config.BeanPostProcessor;

import java.util.Objects;
import java.util.Optional;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;

/**
 * Dynamic thread-pool post processor.
 * BeanPostProcessor 接口定义了两个主要方法：
 * 1. postProcessBeforeInitialization(Object bean, String beanName)：在 bean 初始化方法
 * （如 @PostConstruct 注解的方法或 InitializingBean 接口的 afterPropertiesSet 方法）调用之前执行。
 * 2. postProcessAfterInitialization(Object bean, String beanName)：在 bean 初始化方法调用之后执行。
 */
@Slf4j
@AllArgsConstructor
public final class DynamicThreadPoolPostProcessor implements BeanPostProcessor {

    // 配置信息对象
    private final BootstrapConfigProperties configProperties;

    private static final int DEFAULT_ACTIVE_ALARM = 80;

    private static final int DEFAULT_CAPACITY_ALARM = 80;

    private static final int DEFAULT_INTERVAL = 5;

    private static final String DEFAULT_RECEIVES = "";

    // bean前置处理方法
    @Override
    public Object postProcessBeforeInitialization(Object bean, String beanName) {
        return bean;
    }

    // bean后置处理方法
    // 在这里判断bean是否为动态线程池对象，如果是的话就可以把动态线程池信息注册到服务端
    // 这个方法就是本类最核心的方法，用来处理DynamicThreadPoolExecutor对象
    @Override
    public Object postProcessAfterInitialization(Object bean, String beanName) throws BeansException {
        // 这里会先判断一下传进来的bean是否属于DynamicThreadPoolExecutor类型，如果大家看了我在DynamicThreadPoolConfig类提供的几个例子
        // 就会发现我创建动态线程池对象最终是以Executor或者ThreadPoolExecutor形式返回的，如果是以Executor形式返回的，这个Executor接收的还并不是一个DynamicThreadPoolExecutor对象
        // 而是一个ExecutorTtlWrapper对象，这个ExecutorTtlWrapper对象的作用我已经在DynamicThreadPoolConfig类中解释了，这时候，ExecutorTtlWrapper对象肯定就不属于DynamicThreadPoolExecutor类型了
        // 但是先别急，虽然ExecutorTtlWrapper对象不属于DynamicThreadPoolExecutor类型，但是后面的DynamicThreadPoolAdapterChoose.match(bean)这个条件还是可以通过的，所以仍然可以进入下面的分支
        // 那为什么要执行DynamicThreadPoolAdapterChoose.match(bean)这行代码呢？原因也很简单，因为有时候用户可能会使用spring本身的线程池，或者其他第三方形式的线程池，比如ExecutorTtl，比如spring的ThreadPoolTaskExecutor
        // 该动态线程池框架也想收集这些线程池的信息，所以就会在DynamicThreadPoolAdapterChoose.match(bean)中判断程序内是否有这些第三方线程池的适配器，如果有，就可以使用这些适配器把这些第三方线程池转换成DynamicThreadPoolExecutor对象
        // 之后的逻辑就和处理真正的DynamicThreadPoolExecutor对象一样了，无非就是把线程池信息注册到服务端，然后把线程池保存在线程池全局管理器中
        // DynamicThreadPoolAdapterChoose.match(bean)就是判断bean的类型是否为ThreadPoolTaskExecutor、ExecutorTtlWrapper、ExecutorServiceTtlWrapper中的一个，这些都是第三方的线程池
        if (bean instanceof DynamicThreadPoolExecutor || DynamicThreadPoolAdapterChoose.match(bean)) {
            DynamicThreadPool dynamicThreadPool;
            try {
                // 判断该线程池bean对象上是否存在DynamicThreadPool注解
                dynamicThreadPool = ApplicationContextHolder.findAnnotationOnBean(beanName, DynamicThreadPool.class);
                // 如果找不到该注解，就进入下面这个分支
                if (Objects.isNull(dynamicThreadPool)) {
                    // Adapt to lower versions of SpringBoot.
                    // 这里就是为了适配SpringBoot低版本，使用DynamicThreadPoolAnnotationUtil工具再次查找注解
                    dynamicThreadPool = DynamicThreadPoolAnnotationUtil.findAnnotationOnBean(beanName, DynamicThreadPool.class);
                    if (Objects.isNull(dynamicThreadPool)) {
                        // 还是找不到则直接返回bean即可
                        return bean;
                    }
                }
            } catch (Exception ex) {
                log.error("Failed to create dynamic thread pool in annotation mode.", ex);
                return bean;
            }
            // 走到这里意味着当前的bean上有DynamicThreadPool注解，也就意味着是一个动态线程池，下面就要收集动态线程池配置信息了
            // 定义一个动态线程池
            ThreadPoolExecutor dynamicThreadPoolExecutor = DynamicThreadPoolAdapterChoose.unwrap(bean);
            // 下面的if分支会先从适配器中获得真正的动态线程池，如果获得的线程池为空，说明当前bean本身就是动态线程池，如果不为空，则正好得到了真正的动态线程池，并且赋值给dynamicThreadPoolExecutor了
            // 将bean转换为dynamicThreadPoolExecutor类型，确切地说不是把当前要交给容器的这个bean转换成dynamicThreadPoolExecutor对象
            // 实际上ExecutorTtlWrapper只是持有了dynamicThreadPoolExecutor的引用，这里只不过是直接利用反射从ExecutorTtlWrapper把dynamicThreadPoolExecutor对象取出来了
            if (dynamicThreadPoolExecutor == null) {
                dynamicThreadPoolExecutor = (DynamicThreadPoolExecutor) bean;
            }
            // 将刚刚得到的dynamicThreadPoolExecutor对象包装成一个DynamicThreadPoolWrapper对象，这个对象会被交给线程池全局管理器来管理
            // 之后收集线程池运行信息时都要用到这个对象
            // 在这里把动态线程池的信息注册给服务端了
            ThreadPoolExecutor remoteThreadPoolExecutor = fillPoolAndRegister(((DynamicThreadPoolExecutor) dynamicThreadPoolExecutor).getThreadPoolId(), dynamicThreadPoolExecutor);
            DynamicThreadPoolAdapterChoose.replace(bean, remoteThreadPoolExecutor);
            return DynamicThreadPoolAdapterChoose.match(bean) ? bean : remoteThreadPoolExecutor;
        }
        return bean;
    }

    /**
     * Fill the thread pool and register.
     *
     * @param threadPoolId dynamic thread-pool id
     * @param executor     dynamic thread-pool executor
     * fillPoolAndRegister 方法实现思路非常简单，但要执行的操作就稍微多一些了，
     * 我之所以说该方法实现思路简单，是因为在该方法中，只需要把动态线程池的配置信息封装到一个新的对象，
     * 就是我即将要定义的 DynamicThreadPoolRegisterParameter 对象中，
     * 然后将这个对象直接通过 HttpAgent 通信组件发送给服务端即可
     */
    protected ThreadPoolExecutor fillPoolAndRegister(String threadPoolId, ThreadPoolExecutor executor) {
        ExecutorProperties executorProperties = null;
        if (configProperties.getExecutors() != null) {
            // 从配置文件中获取线程池配置信息
            executorProperties = configProperties.getExecutors()
                    .stream()
                    .filter(each -> Objects.equals(threadPoolId, each.getThreadPoolId()))
                    .findFirst()
                    .orElseThrow(() -> new RuntimeException("The thread pool id does not exist in the configuration."));
            try {
                executorProperties = buildActualExecutorProperties(executorProperties);
                threadPoolParamReplace(executor, executorProperties);
            } catch (Exception ex) {
                log.error("Failed to initialize thread pool configuration.", ex);
            }
            ThreadPoolNotifyAlarm threadPoolNotifyAlarm = buildThreadPoolNotifyAlarm(executorProperties);
            GlobalNotifyAlarmManage.put(threadPoolId, threadPoolNotifyAlarm);
        }
        ThreadPoolExecutorRegistry.putHolder(threadPoolId, executor,
                executorProperties == null
                        ? buildDefaultExecutorProperties(threadPoolId, executor)
                        : executorProperties);
        // GlobalThreadPoolManage.registerPool(dynamicThreadPoolWrapper.getThreadPoolId(), dynamicThreadPoolWrapper);
        // GlobalConfigThreadPoolManage.register(
        // threadPoolId,
        // executorProperties == null
        // ? buildDefaultExecutorProperties(threadPoolId, executor)
        // : executorProperties);
        return executor;
    }

    /**
     * Build actual executor properties.
     *
     * @param executorProperties executor properties
     * @return executor properties
     */
    private ExecutorProperties buildActualExecutorProperties(ExecutorProperties executorProperties) {
        return Optional.ofNullable(configProperties.getDefaultExecutor()).map(each -> buildExecutorProperties(executorProperties)).orElse(executorProperties);
    }

    /**
     * Build default executor properties.
     *
     * @param threadPoolId thread-pool id
     * @param executor     dynamic thread-pool executor
     * @return executor properties
     */
    private ExecutorProperties buildDefaultExecutorProperties(String threadPoolId, ThreadPoolExecutor executor) {
        ExecutorProperties executorProperties = new ExecutorProperties();
        BlockingQueue<Runnable> blockingQueue = executor.getQueue();
        int queueSize = blockingQueue.size();
        String queueType = blockingQueue.getClass().getSimpleName();
        int remainingCapacity = blockingQueue.remainingCapacity();
        int queueCapacity = queueSize + remainingCapacity;
        executorProperties.setCorePoolSize(executor.getCorePoolSize())
                .setMaximumPoolSize(executor.getMaximumPoolSize())
                .setAllowCoreThreadTimeOut(executor.allowsCoreThreadTimeOut())
                .setKeepAliveTime(executor.getKeepAliveTime(TimeUnit.SECONDS))
                .setBlockingQueue(queueType)
                .setExecuteTimeOut(Constants.EXECUTE_TIME_OUT)
                .setQueueCapacity(queueCapacity)
                .setRejectedHandler(executor.getRejectedExecutionHandler().getClass().getSimpleName())
                .setThreadPoolId(threadPoolId);
        return executorProperties;
    }

    /**
     * Thread-pool param replace.
     *
     * @param executor           dynamic thread-pool executor
     * @param executorProperties executor properties
     */
    private void threadPoolParamReplace(ThreadPoolExecutor executor, ExecutorProperties executorProperties) {
        BlockingQueue workQueue = BlockingQueueTypeEnum.createBlockingQueue(executorProperties.getBlockingQueue(), executorProperties.getQueueCapacity());
        ReflectUtil.setFieldValue(executor, "workQueue", workQueue);
        // fix https://github.com/opengoofy/hippo4j/issues/1063
        ThreadPoolExecutorUtil.safeSetPoolSize(executor, executorProperties.getCorePoolSize(), executorProperties.getMaximumPoolSize());
        executor.setKeepAliveTime(executorProperties.getKeepAliveTime(), TimeUnit.SECONDS);
        executor.allowCoreThreadTimeOut(executorProperties.getAllowCoreThreadTimeOut());
        executor.setRejectedExecutionHandler(RejectedPolicyTypeEnum.createPolicy(executorProperties.getRejectedHandler()));
        if (executor instanceof DynamicThreadPoolExecutor) {
            Optional.ofNullable(executorProperties.getExecuteTimeOut())
                    .ifPresent(executeTimeOut -> ((DynamicThreadPoolExecutor) executor).setExecuteTimeOut(executeTimeOut));
        }
    }

    /**
     * Build executor properties.
     *
     * @param executorProperties executor properties
     * @return executor properties
     */
    private ExecutorProperties buildExecutorProperties(ExecutorProperties executorProperties) {
        return ExecutorProperties.builder()
                .corePoolSize(Optional.ofNullable(executorProperties.getCorePoolSize())
                        .orElseGet(() -> Optional.ofNullable(configProperties.getDefaultExecutor()).map(ExecutorProperties::getCorePoolSize).get()))
                .maximumPoolSize(Optional.ofNullable(executorProperties.getMaximumPoolSize())
                        .orElseGet(() -> Optional.ofNullable(configProperties.getDefaultExecutor()).map(ExecutorProperties::getMaximumPoolSize).get()))
                .allowCoreThreadTimeOut(Optional.ofNullable(executorProperties.getAllowCoreThreadTimeOut())
                        .orElseGet(() -> Optional.ofNullable(configProperties.getDefaultExecutor()).map(ExecutorProperties::getAllowCoreThreadTimeOut).get()))
                .keepAliveTime(Optional.ofNullable(executorProperties.getKeepAliveTime())
                        .orElseGet(() -> Optional.ofNullable(configProperties.getDefaultExecutor()).map(ExecutorProperties::getKeepAliveTime).get()))
                .blockingQueue(Optional.ofNullable(executorProperties.getBlockingQueue())
                        .orElseGet(() -> Optional.ofNullable(configProperties.getDefaultExecutor()).map(ExecutorProperties::getBlockingQueue).get()))
                .executeTimeOut(Optional.ofNullable(executorProperties.getExecuteTimeOut())
                        .orElseGet(() -> Optional.ofNullable(configProperties.getDefaultExecutor()).map(ExecutorProperties::getExecuteTimeOut).orElse(0L)))
                .queueCapacity(Optional.ofNullable(executorProperties.getQueueCapacity())
                        .orElseGet(() -> Optional.ofNullable(configProperties.getDefaultExecutor()).map(ExecutorProperties::getQueueCapacity).get()))
                .rejectedHandler(Optional.ofNullable(executorProperties.getRejectedHandler())
                        .orElseGet(() -> Optional.ofNullable(configProperties.getDefaultExecutor()).map(ExecutorProperties::getRejectedHandler).get()))
                .threadNamePrefix(StringUtil.isBlank(executorProperties.getThreadNamePrefix()) ? executorProperties.getThreadPoolId() : executorProperties.getThreadNamePrefix())
                .threadPoolId(executorProperties.getThreadPoolId())
                .alarm(Optional.ofNullable(executorProperties.getAlarm())
                        .orElseGet(() -> Optional.ofNullable(configProperties.getDefaultExecutor()).map(ExecutorProperties::getAlarm).orElse(null)))
                .activeAlarm(Optional.ofNullable(executorProperties.getActiveAlarm())
                        .orElseGet(() -> Optional.ofNullable(configProperties.getDefaultExecutor()).map(ExecutorProperties::getActiveAlarm).orElse(null)))
                .capacityAlarm(Optional.ofNullable(executorProperties.getCapacityAlarm())
                        .orElseGet(() -> Optional.ofNullable(configProperties.getDefaultExecutor()).map(ExecutorProperties::getCapacityAlarm).orElse(null)))
                .notify(Optional.ofNullable(executorProperties.getNotify())
                        .orElseGet(() -> Optional.ofNullable(configProperties.getDefaultExecutor()).map(ExecutorProperties::getNotify).orElse(null)))
                .nodes(Optional.ofNullable(executorProperties.getNodes())
                        .orElseGet(() -> Optional.ofNullable(configProperties.getDefaultExecutor()).map(ExecutorProperties::getNodes).orElse(null)))
                .build();
    }

    /**
     * Build thread-pool notify alarm
     *
     * @param executorProperties executor properties
     * @return thread-pool notify alarm
     */
    private ThreadPoolNotifyAlarm buildThreadPoolNotifyAlarm(ExecutorProperties executorProperties) {
        ExecutorNotifyProperties notify = Optional.ofNullable(executorProperties).map(ExecutorProperties::getNotify).orElse(null);
        boolean isAlarm = Optional.ofNullable(executorProperties.getAlarm())
                .orElseGet(() -> Optional.ofNullable(configProperties.getDefaultExecutor()).map(ExecutorProperties::getAlarm).orElse(true));
        int activeAlarm = Optional.ofNullable(executorProperties.getActiveAlarm())
                .orElseGet(() -> Optional.ofNullable(configProperties.getDefaultExecutor()).map(ExecutorProperties::getActiveAlarm).orElse(DEFAULT_ACTIVE_ALARM));
        int capacityAlarm = Optional.ofNullable(executorProperties.getCapacityAlarm())
                .orElseGet(() -> Optional.ofNullable(configProperties.getDefaultExecutor()).map(ExecutorProperties::getCapacityAlarm).orElse(DEFAULT_CAPACITY_ALARM));
        int interval = Optional.ofNullable(notify)
                .map(ExecutorNotifyProperties::getInterval)
                .orElseGet(() -> Optional.ofNullable(configProperties.getDefaultExecutor()).map(ExecutorProperties::getNotify).map(ExecutorNotifyProperties::getInterval).orElse(DEFAULT_INTERVAL));
        String receive = Optional.ofNullable(notify)
                .map(ExecutorNotifyProperties::getReceives)
                .orElseGet(() -> Optional.ofNullable(configProperties.getDefaultExecutor()).map(ExecutorProperties::getNotify).map(ExecutorNotifyProperties::getReceives).orElse(DEFAULT_RECEIVES));
        ThreadPoolNotifyAlarm threadPoolNotifyAlarm = new ThreadPoolNotifyAlarm(isAlarm, activeAlarm, capacityAlarm);
        threadPoolNotifyAlarm.setInterval(interval);
        threadPoolNotifyAlarm.setReceives(receive);
        return threadPoolNotifyAlarm;
    }
}
