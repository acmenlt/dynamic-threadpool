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

package cn.hippo4j.core.executor;

import cn.hippo4j.common.toolkit.CollectionUtil;
import cn.hippo4j.core.executor.plugin.impl.TaskDecoratorPlugin;
import cn.hippo4j.core.executor.plugin.impl.TaskRejectCountRecordPlugin;
import cn.hippo4j.core.executor.plugin.impl.TaskTimeoutNotifyAlarmPlugin;
import cn.hippo4j.core.executor.plugin.impl.ThreadPoolExecutorShutdownPlugin;
import cn.hippo4j.core.executor.plugin.manager.DefaultThreadPoolPluginManager;
import cn.hippo4j.core.executor.plugin.manager.DefaultThreadPoolPluginRegistrar;
import cn.hippo4j.threadpool.alarm.handler.DefaultThreadPoolCheckAlarmHandler;
import lombok.Getter;
import lombok.NonNull;
import lombok.Setter;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.DisposableBean;
import org.springframework.core.annotation.AnnotationAwareOrderComparator;
import org.springframework.core.task.TaskDecorator;

import java.util.Objects;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.RejectedExecutionHandler;
import java.util.concurrent.ThreadFactory;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicLong;

import static cn.hippo4j.common.constant.Constants.NO_REJECT_COUNT_NUM;

/**
 * Enhanced dynamic and monitored thread pool.
 *
 * @see DefaultThreadPoolCheckAlarmHandler#buildAlarmNotifyRequest
 */
@Slf4j
public class DynamicThreadPoolExecutor extends ExtensibleThreadPoolExecutor implements DisposableBean {

    /**
     * A flag used to indicate whether destroy() method has been called,
     * after the flag is set to false, calling destroy() method again will not take effect
     */
    private final AtomicBoolean active;

    /**
     * Wait for tasks to complete on shutdown
     *  //关闭线程池时是否等待任务执行完毕
     */
    @Getter
    @Setter
    private boolean waitForTasksToCompleteOnShutdown;

    /**
     * The default await termination millis
     */
    private static final Long DEFAULT_AWAIT_TERMINATION_MILLIS = -1L;

    /**
     * The default execute timeout
     */
    private static final Long DEFAULT_EXECUTE_TIMEOUT = -1L;

    /**
     * Creates a new {@code DynamicThreadPoolExecutor} with the given initial parameters.
     *
     * @param threadPoolId                     thread-pool id
     * @param executeTimeOut                   execute time out
     * @param waitForTasksToCompleteOnShutdown wait for tasks to complete on shutdown
     * @param awaitTerminationMillis           await termination millis
     * @param corePoolSize                     the number of threads to keep in the pool, even
     *                                         if they are idle, unless {@code allowCoreThreadTimeOut} is set
     * @param maximumPoolSize                  the maximum number of threads to allow in the
     *                                         pool
     * @param keepAliveTime                    when the number of threads is greater than
     *                                         the core, this is the maximum time that excess idle threads
     *                                         will wait for new tasks before terminating.
     * @param unit                             the time unit for the {@code keepAliveTime} argument
     * @param blockingQueue                    the queue to use for holding tasks before they are
     *                                         executed.  This queue will hold only the {@code Runnable}
     *                                         tasks submitted by the {@code execute} method.
     * @param threadFactory                    the factory to use when the executor creates a new thread
     * @param rejectedExecutionHandler         the handler to use when execution is blocked because the thread bounds and queue capacities are reached
     * @throws IllegalArgumentException if one of the following holds:<br>
     *                                  {@code corePoolSize < 0}<br>
     *                                  {@code keepAliveTime < 0}<br>
     *                                  {@code maximumPoolSize <= 0}<br>
     *                                  {@code maximumPoolSize < corePoolSize}
     * @throws NullPointerException     if {@code workQueue}
     *                                  or {@code threadFactory} or {@code handler} is null
     */
    //构造方法
    //这个构造方法中有两个参数，一个是executeTimeOut，这个就是用户设置的任务执行的超时时间，这个参数该怎么发挥作用呢？
    //另一个是awaitTerminationMillis，代表线程池关闭时，等待剩余任务执行的最大时间，那这个参数又该怎么发挥作用呢？
    public DynamicThreadPoolExecutor(
                                     int corePoolSize, int maximumPoolSize,
                                     long keepAliveTime, TimeUnit unit,
                                     long executeTimeOut, boolean waitForTasksToCompleteOnShutdown, long awaitTerminationMillis,
                                     @NonNull BlockingQueue<Runnable> blockingQueue,
                                     @NonNull String threadPoolId,
                                     @NonNull ThreadFactory threadFactory,
                                     @NonNull RejectedExecutionHandler rejectedExecutionHandler) {
        // 可以看到向父类构造方法传入了一个 threadPoolId 参数，threadPoolId 就是动态线程池的 Id。
        // 每一个动态线程池都有自己的 Id，用来和其他动态线程池做区分
        super(
                // 这里给ExtensibleThreadPoolExecutor 的构造方法传入了一个 DefaultThreadPoolPluginManager 参数
                threadPoolId, new DefaultThreadPoolPluginManager().setPluginComparator(AnnotationAwareOrderComparator.INSTANCE),
                corePoolSize, maximumPoolSize, keepAliveTime, unit,
                blockingQueue, threadFactory, rejectedExecutionHandler);
        log.info("Initializing ExecutorService '{}'", threadPoolId);
        this.waitForTasksToCompleteOnShutdown = waitForTasksToCompleteOnShutdown;
        // Init default plugins.
        //在这里创建了默认的插件管理器DefaultThreadPoolPluginManager对象
        //注意，这里有一行非常重要的代码，这里的操作非常重要，就是在这里，把所有插件都注册到当前动态线程池对象的插件管理器中了
        //这时候，executeTimeOut和awaitTerminationMillis参数也就可以发挥作用了
        new DefaultThreadPoolPluginRegistrar(executeTimeOut, awaitTerminationMillis)
                .doRegister(this);
        this.active = new AtomicBoolean(true);
    }

    /**
     * <p>Whether the current instance is in the active state. <br />
     * It returns false when the xx method is called at least once.
     *
     * @return true if current instance is in the active state, false otherwise
     */
    public boolean isActive() {
        return active.get();
    }

    /**
     * Invoked by the containing {@code BeanFactory} on destruction of a bean.
     */
    //销毁动态线程池的方法
    @Override
    public void destroy() {
        // instance has been destroyed, not need to call this method again
        if (!isActive()) {
            log.warn("Failed to destroy ExecutorService '{}' because it has already been destroyed", getThreadPoolId());
            return;
        }
        //如果等待任务执行完毕再终止线程池工作，就调用shutdown方法
        if (isWaitForTasksToCompleteOnShutdown()) {
            super.shutdown();
        } else {
            super.shutdownNow();
        }
        //在这里清空插件管理器中的插件
        getThreadPoolPluginManager().clear();
        log.info("ExecutorService '{}' has been destroyed", getThreadPoolId());

        // modify the flag to false avoid the method being called repeatedly
        active.set(false);
    }

    /**
     * Get await termination millis.
     *
     * @return await termination millis.
     * @deprecated use {@link ThreadPoolExecutorShutdownPlugin}
     */
    @Deprecated
    public long getAwaitTerminationMillis() {
        return getPluginOfType(ThreadPoolExecutorShutdownPlugin.PLUGIN_NAME, ThreadPoolExecutorShutdownPlugin.class)
                .map(ThreadPoolExecutorShutdownPlugin::getAwaitTerminationMillis)
                .orElse(DEFAULT_AWAIT_TERMINATION_MILLIS);
    }

    /**
     * Set support param.
     *
     * @param awaitTerminationMillis           await termination millis
     * @param waitForTasksToCompleteOnShutdown wait for tasks to complete on shutdown
     * @deprecated use {@link ThreadPoolExecutorShutdownPlugin}
     */
    @Deprecated
    public void setSupportParam(long awaitTerminationMillis, boolean waitForTasksToCompleteOnShutdown) {
        setWaitForTasksToCompleteOnShutdown(waitForTasksToCompleteOnShutdown);
        getPluginOfType(ThreadPoolExecutorShutdownPlugin.PLUGIN_NAME, ThreadPoolExecutorShutdownPlugin.class)
                .ifPresent(processor -> processor.setAwaitTerminationMillis(awaitTerminationMillis));
    }

    /**
     * Get reject count num.
     *
     * @return reject count num
     * @see DefaultThreadPoolCheckAlarmHandler#buildAlarmNotifyRequest
     * @deprecated use {@link TaskRejectCountRecordPlugin}
     */
    @Deprecated
    public Long getRejectCountNum() {
        return getPluginOfType(TaskRejectCountRecordPlugin.PLUGIN_NAME, TaskRejectCountRecordPlugin.class)
                .map(TaskRejectCountRecordPlugin::getRejectCountNum)
                .orElse(NO_REJECT_COUNT_NUM);
    }

    /**
     * Get reject count.
     *
     * @return reject count num
     * @deprecated use {@link TaskRejectCountRecordPlugin}
     */
    @Deprecated
    public AtomicLong getRejectCount() {
        return getPluginOfType(TaskRejectCountRecordPlugin.PLUGIN_NAME, TaskRejectCountRecordPlugin.class)
                .map(TaskRejectCountRecordPlugin::getRejectCount)
                .orElse(new AtomicLong(0));
    }

    /**
     * Get execute time out.
     *
     * @deprecated use {@link TaskTimeoutNotifyAlarmPlugin}
     */
    @Deprecated
    public Long getExecuteTimeOut() {
        return getPluginOfType(TaskTimeoutNotifyAlarmPlugin.PLUGIN_NAME, TaskTimeoutNotifyAlarmPlugin.class)
                .map(TaskTimeoutNotifyAlarmPlugin::getExecuteTimeOut)
                .orElse(DEFAULT_EXECUTE_TIMEOUT);
    }

    /**
     * Set execute time out.
     *
     * @param executeTimeOut execute time out
     * @deprecated use {@link TaskTimeoutNotifyAlarmPlugin}
     */
    @Deprecated
    public void setExecuteTimeOut(Long executeTimeOut) {
        getPluginOfType(TaskTimeoutNotifyAlarmPlugin.PLUGIN_NAME, TaskTimeoutNotifyAlarmPlugin.class)
                .ifPresent(processor -> processor.setExecuteTimeOut(executeTimeOut));
    }

    /**
     * Get {@link TaskDecorator}.
     *
     * @deprecated use {@link TaskDecoratorPlugin}
     */
    @Deprecated
    public TaskDecorator getTaskDecorator() {
        return getPluginOfType(TaskDecoratorPlugin.PLUGIN_NAME, TaskDecoratorPlugin.class)
                .map(processor -> CollectionUtil.getFirst(processor.getDecorators()))
                .orElse(null);
    }

    /**
     * Set {@link TaskDecorator}.
     *
     * @param taskDecorator task decorator
     * @deprecated use {@link TaskDecoratorPlugin}
     */
    @Deprecated
    public void setTaskDecorator(TaskDecorator taskDecorator) {
        getPluginOfType(TaskDecoratorPlugin.PLUGIN_NAME, TaskDecoratorPlugin.class)
                .ifPresent(processor -> {
                    if (Objects.nonNull(taskDecorator)) {
                        processor.clearDecorators();
                        processor.addDecorator(taskDecorator);
                    }
                });
    }

    /**
     * Get rejected execution handler.
     *
     * @deprecated use {@link DynamicThreadPoolExecutor#getRejectedExecutionHandler}
     */
    @Deprecated
    public RejectedExecutionHandler getRedundancyHandler() {
        return getRejectedExecutionHandler();
    }

    /**
     * Set rejected execution handler.
     *
     * @param handler handler
     * @deprecated use {@link DynamicThreadPoolExecutor#setRejectedExecutionHandler}
     */
    @Deprecated
    public void setRedundancyHandler(RejectedExecutionHandler handler) {
        setRejectedExecutionHandler(handler);
    }
}
