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

package cn.hippo4j.core.executor.support;

import cn.hippo4j.common.extension.design.Builder;
import cn.hippo4j.common.executor.support.BlockingQueueTypeEnum;
import cn.hippo4j.common.toolkit.Assert;
import org.springframework.core.task.TaskDecorator;

import java.math.BigDecimal;
import java.util.Optional;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.RejectedExecutionHandler;
import java.util.concurrent.ThreadFactory;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;

/**
 * Thread-pool builder.
 */
public class ThreadPoolBuilder implements Builder<ThreadPoolExecutor> {

    //这个表示线程池构建器是否要创建快速线程池，所谓快速线程池
    //其实就是使用的任务队列特殊一点，使用了TaskQueue任务队列
    //这个队列有一个特点，那就是只要当提交给任务队列的数量大于线程池当前线程的数量了
    //但是当前线程数量仍然小于线程池的最大线程数量，这时候就可以不把任务添加到队列中
    //而是让线程池直接创建线程执行任务，所谓快速，就快速在这里。实际上在hippo4j框架中
    //不仅提供了动态线程池，还提供了FastThreadPoolExecutor快速线程池，最后还提供了普通线程池，也就是原生的ThreadPoolExecutor。
    //这三种线程池想创建哪一种就创建哪一种，只不过只有DynamicThreadPoolExecutor动态线程池会被注册到服务单，并且可以动态更新配置信息
    //快速线程池和普通线程池我就不在文章中展示了，反正所有线程池的创建都在ThreadPoolBuilder体系下，大家掌握了DynamicThreadPoolExecutor是如何创建的
    //其他的也就都掌握了
    private boolean isFastPool;

    //表示这个线程池构建器是否要创建动态线程池
    private boolean isDynamicPool;

    //默认的线程池的核心线程数量，这个数量是根据CPU核心数量计算出来的
    private int corePoolSize = calculateCoreNum();

    //默认的池最大线程数量
    private int maximumPoolSize = corePoolSize + (corePoolSize >> 1);

    //默认存活时间
    private long keepAliveTime = 30000L;

    private TimeUnit timeUnit = TimeUnit.MILLISECONDS;

    private long executeTimeOut = 10000L;

    private int capacity = 512;

    private BlockingQueueTypeEnum blockingQueueType = BlockingQueueTypeEnum.LINKED_BLOCKING_QUEUE;

    private BlockingQueue workQueue;

    private RejectedExecutionHandler rejectedExecutionHandler = new ThreadPoolExecutor.AbortPolicy();

    private boolean isDaemon = false;

    private String threadNamePrefix;

    private ThreadFactory threadFactory;

    private String threadPoolId;

    private TaskDecorator taskDecorator;

    private Long awaitTerminationMillis = 5000L;

    private Boolean waitForTasksToCompleteOnShutdown = true;

    private Boolean allowCoreThreadTimeOut = false;

    /**
     * Calculate core num.
     *
     * @return core num
     */
    private Integer calculateCoreNum() {
        int cpuCoreNum = Runtime.getRuntime().availableProcessors();
        return new BigDecimal(cpuCoreNum).divide(new BigDecimal("0.2")).intValue();
    }

    /**
     * Is fast pool.
     *
     * @param isFastPool is fast pool
     * @return thread-pool builder
     */
    public ThreadPoolBuilder isFastPool(Boolean isFastPool) {
        this.isFastPool = isFastPool;
        return this;
    }

    /**
     * Dynamic pool.
     *
     * @return thread-pool builder
     */
    public ThreadPoolBuilder dynamicPool() {
        this.isDynamicPool = true;
        return this;
    }

    /**
     * Thread factory.
     *
     * @param threadNamePrefix thread name prefix
     * @return thread-pool builder
     */
    public ThreadPoolBuilder threadFactory(String threadNamePrefix) {
        this.threadNamePrefix = threadNamePrefix;
        return this;
    }

    /**
     * Thread factory.
     *
     * @param threadFactory thread factory
     * @return thread-pool builder
     */
    public ThreadPoolBuilder threadFactory(ThreadFactory threadFactory) {
        this.threadFactory = threadFactory;
        return this;
    }

    /**
     * Thread factory.
     *
     * @param threadNamePrefix thread name prefix
     * @param isDaemon         is daemon
     * @return thread-pool builder
     */
    public ThreadPoolBuilder threadFactory(String threadNamePrefix, Boolean isDaemon) {
        this.threadNamePrefix = threadNamePrefix;
        this.isDaemon = isDaemon;
        return this;
    }

    /**
     * Core pool size.
     *
     * @param corePoolSize core pool size
     * @return thread-pool builder
     */
    public ThreadPoolBuilder corePoolSize(int corePoolSize) {
        this.corePoolSize = corePoolSize;
        return this;
    }

    /**
     * @deprecated Use {@link #maximumPoolSize}
     */
    @Deprecated
    public ThreadPoolBuilder maxPoolNum(int maximumPoolSize) {
        return this.maximumPoolSize(maximumPoolSize);
    }

    /**
     * Max pool num.
     *
     * @param maximumPoolSize max pool num
     * @return thread-pool builder
     */
    public ThreadPoolBuilder maximumPoolSize(int maximumPoolSize) {
        this.maximumPoolSize = maximumPoolSize;
        if (maximumPoolSize < this.corePoolSize) {
            this.corePoolSize = maximumPoolSize;
        }
        return this;
    }

    /**
     * Single pool.
     *
     * @return thread-pool builder
     */
    public ThreadPoolBuilder singlePool() {
        int singleNum = 1;
        this.corePoolSize = singleNum;
        this.maximumPoolSize = singleNum;
        return this;
    }

    /**
     * Single pool.
     *
     * @param threadNamePrefix thread name prefix
     * @return thread-pool builder
     */
    public ThreadPoolBuilder singlePool(String threadNamePrefix) {
        int singleNum = 1;
        this.corePoolSize = singleNum;
        this.maximumPoolSize = singleNum;
        this.threadNamePrefix = threadNamePrefix;
        return this;
    }

    /**
     * Pool thread size.
     *
     * @param corePoolSize    core pool size
     * @param maximumPoolSize max pool size
     * @return thread-pool builder
     */
    public ThreadPoolBuilder poolThreadSize(int corePoolSize, int maximumPoolSize) {
        this.corePoolSize = corePoolSize;
        this.maximumPoolSize = maximumPoolSize;
        return this;
    }

    /**
     * Keep alive time.
     *
     * @param keepAliveTime keep alive time
     * @return thread-pool builder
     */
    public ThreadPoolBuilder keepAliveTime(long keepAliveTime) {
        this.keepAliveTime = keepAliveTime;
        return this;
    }

    /**
     * Time unit.
     *
     * @param timeUnit time unit
     * @return thread-pool builder
     */
    public ThreadPoolBuilder timeUnit(TimeUnit timeUnit) {
        this.timeUnit = timeUnit;
        return this;
    }

    /**
     * Execute time-out.
     *
     * @param executeTimeOut execute time-out
     * @return thread-pool builder
     */
    public ThreadPoolBuilder executeTimeOut(long executeTimeOut) {
        this.executeTimeOut = executeTimeOut;
        return this;
    }

    /**
     * Keep alive time.
     *
     * @param keepAliveTime keep alive time
     * @param timeUnit      time unit
     * @return thread-pool builder
     */
    public ThreadPoolBuilder keepAliveTime(long keepAliveTime, TimeUnit timeUnit) {
        this.keepAliveTime = keepAliveTime;
        this.timeUnit = timeUnit;
        return this;
    }

    /**
     * Capacity.
     *
     * @param capacity capacity
     * @return thread-pool builder
     */
    public ThreadPoolBuilder capacity(int capacity) {
        this.capacity = capacity;
        return this;
    }

    /**
     * Work queue.
     *
     * @param queueType queue type
     * @param capacity  capacity
     * @return thread-pool builder
     */
    public ThreadPoolBuilder workQueue(BlockingQueueTypeEnum queueType, int capacity) {
        this.blockingQueueType = queueType;
        this.capacity = capacity;
        return this;
    }

    /**
     * Rejected.
     *
     * @param rejectedExecutionHandler rejected execution handler
     * @return thread-pool builder
     */
    public ThreadPoolBuilder rejected(RejectedExecutionHandler rejectedExecutionHandler) {
        this.rejectedExecutionHandler = rejectedExecutionHandler;
        return this;
    }

    /**
     * Work queue.
     *
     * @param blockingQueueType blocking queue type
     * @return thread-pool builder
     */
    public ThreadPoolBuilder workQueue(BlockingQueueTypeEnum blockingQueueType) {
        this.blockingQueueType = blockingQueueType;
        return this;
    }

    /**
     * Work queue.
     *
     * @param workQueue work queue
     * @return thread-pool builder
     */
    public ThreadPoolBuilder workQueue(BlockingQueue workQueue) {
        this.workQueue = workQueue;
        return this;
    }

    /**
     * Thread-pool id.
     *
     * @param threadPoolId thread-pool id
     * @return thread-pool builder
     */
    public ThreadPoolBuilder threadPoolId(String threadPoolId) {
        this.threadPoolId = threadPoolId;
        return this;
    }

    /**
     * Task decorator.
     *
     * @param taskDecorator task decorator
     * @return thread-pool builder
     */
    public ThreadPoolBuilder taskDecorator(TaskDecorator taskDecorator) {
        this.taskDecorator = taskDecorator;
        return this;
    }

    /**
     * Await termination millis.
     *
     * @param awaitTerminationMillis await termination millis
     * @return thread-pool builder
     */
    public ThreadPoolBuilder awaitTerminationMillis(long awaitTerminationMillis) {
        this.awaitTerminationMillis = awaitTerminationMillis;
        return this;
    }

    /**
     * Wait for tasks to complete on shutdown.
     *
     * @param waitForTasksToCompleteOnShutdown wait for tasks to complete on shutdown
     * @return thread-pool builder
     */
    public ThreadPoolBuilder waitForTasksToCompleteOnShutdown(boolean waitForTasksToCompleteOnShutdown) {
        this.waitForTasksToCompleteOnShutdown = waitForTasksToCompleteOnShutdown;
        return this;
    }

    /**
     * Dynamic support.
     *
     * @param waitForTasksToCompleteOnShutdown wait for tasks to complete on shutdown
     * @param awaitTerminationMillis           await termination millis
     * @return thread-pool builder
     */
    public ThreadPoolBuilder dynamicSupport(boolean waitForTasksToCompleteOnShutdown, long awaitTerminationMillis) {
        this.awaitTerminationMillis = awaitTerminationMillis;
        this.waitForTasksToCompleteOnShutdown = waitForTasksToCompleteOnShutdown;
        return this;
    }

    /**
     * Allow core thread time-out.
     *
     * @param allowCoreThreadTimeOut core thread time-out
     * @return thread-pool builder
     */
    public ThreadPoolBuilder allowCoreThreadTimeOut(boolean allowCoreThreadTimeOut) {
        this.allowCoreThreadTimeOut = allowCoreThreadTimeOut;
        return this;
    }

    /**
     * Builder design pattern implementation.
     *
     * @return thread-pool builder
     */
    public static ThreadPoolBuilder builder() {
        return new ThreadPoolBuilder();
    }

    /**
     * Create dynamic thread pool by thread pool id.
     *
     * @param threadPoolId thread-pool id
     * @return dynamic thread-pool executor
     */
    public static ThreadPoolExecutor buildDynamicPoolById(String threadPoolId) {
        return ThreadPoolBuilder.builder().threadFactory(threadPoolId).threadPoolId(threadPoolId).dynamicPool().build();
    }

    /**
     * Build a normal thread-pool with {@code builder}.
     *
     * @param builder thread-pool builder
     * @return normal thread-pool
     */
    private static ThreadPoolExecutor buildPool(ThreadPoolBuilder builder) {
        return AbstractBuildThreadPoolTemplate.buildPool(buildInitParam(builder));
    }

    /**
     * Build a dynamic thread-pool with {@code builder}.
     *
     * @param builder thread-pool builder
     * @return dynamic thread-pool executor
     */
    private static ThreadPoolExecutor buildDynamicPool(ThreadPoolBuilder builder) {
        //AbstractBuildThreadPoolTemplate.buildDynamicPool()方法中又调用了当前构建器对象的buildInitParam(builder)方法
        //buildInitParam(builder)方法会得到一个ThreadPoolInitParam线程池初始化参数对象，该对象封装了线程池需要的配置参数
        //线程池构建器模板就会使用这个参数对象创建动态线程池
        //而线程池构建器模板就是AbstractBuildThreadPoolTemplate，至于为什么是抽象的，是因为这个模板不仅可以创建动态线程池
        //还可以创建动态线程池，还有普通线程池，这个就不再过多解释了
        return AbstractBuildThreadPoolTemplate.buildDynamicPool(buildInitParam(builder));
    }

    /**
     * Build thread-pool initialization parameters via {@code builder}.
     *
     * @param builder thread-pool builder
     * @return thread-pool init param
     */
    private static AbstractBuildThreadPoolTemplate.ThreadPoolInitParam buildInitParam(ThreadPoolBuilder builder) {
        //定义一个ThreadPoolInitParam对象
        AbstractBuildThreadPoolTemplate.ThreadPoolInitParam initParam;
        if (builder.threadFactory == null) {
            //判断线程工厂是否为空，如果线程工厂为空，就判断线程池中线程前缀是否为空
            //如果线程前缀不为空，则直接创建ThreadPoolInitParam对象即可，这里创建ThreadPoolInitParam对象的过程中，其实就把ThreadPoolInitParam
            //对象内部的ThreadFactory成员变量创建成功了
            Assert.notEmpty(builder.threadNamePrefix, "The thread name prefix cannot be empty or an empty string.");
            initParam = new AbstractBuildThreadPoolTemplate.ThreadPoolInitParam(builder.threadNamePrefix, builder.isDaemon);
        } else {
            //如果线程工厂不为空，直接创建ThreadPoolInitParam对象即可
            initParam = new AbstractBuildThreadPoolTemplate.ThreadPoolInitParam(builder.threadFactory);
        }
        //接下来就要使用刚才得到的构造器对象给initParam中的其他成员变量赋值即可
        initParam.setCorePoolNum(builder.corePoolSize)
                .setMaximumPoolSize(builder.maximumPoolSize)
                .setKeepAliveTime(builder.keepAliveTime)
                .setCapacity(builder.capacity)
                .setExecuteTimeOut(builder.executeTimeOut)
                .setRejectedExecutionHandler(builder.rejectedExecutionHandler)
                .setTimeUnit(builder.timeUnit)
                .setAllowCoreThreadTimeOut(builder.allowCoreThreadTimeOut)
                .setTaskDecorator(builder.taskDecorator);
        //判断用户要创建的是什么线程池
        if (builder.isDynamicPool) {
            //这里创建的就是动态线程池得到线程池Id
            String threadPoolId = Optional.ofNullable(builder.threadPoolId).orElse(builder.threadNamePrefix);
            //设置线程池Id
            initParam.setThreadPoolId(threadPoolId);
            //设置线程池关闭时是否等待正在执行的任务执行完毕
            initParam.setWaitForTasksToCompleteOnShutdown(builder.waitForTasksToCompleteOnShutdown);
            //设置线程池关闭时，等待剩余任务执行的最大时间
            initParam.setAwaitTerminationMillis(builder.awaitTerminationMillis);
        }
        if (!builder.isFastPool) {
            if (builder.workQueue == null) {
                if (builder.blockingQueueType == null) {
                    builder.blockingQueueType = BlockingQueueTypeEnum.LINKED_BLOCKING_QUEUE;
                }
                builder.workQueue = BlockingQueueTypeEnum.createBlockingQueue(builder.blockingQueueType.getType(), builder.capacity);
            }
            initParam.setWorkQueue(builder.workQueue);
        }
        return initParam;
    }

    @Override
    public ThreadPoolExecutor build() {
        return isDynamicPool ? buildDynamicPool(this) : buildPool(this);
    }
}
