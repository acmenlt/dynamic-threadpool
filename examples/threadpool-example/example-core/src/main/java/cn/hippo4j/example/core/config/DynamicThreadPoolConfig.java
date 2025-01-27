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

package cn.hippo4j.example.core.config;

import cn.hippo4j.core.executor.DynamicThreadPool;
import cn.hippo4j.core.executor.SpringDynamicThreadPool;
import cn.hippo4j.core.executor.support.ThreadPoolBuilder;
import cn.hippo4j.example.core.handler.TaskTraceBuilderHandler;
import cn.hippo4j.example.core.inittest.TaskDecoratorTest;
import com.alibaba.ttl.threadpool.TtlExecutors;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;

import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.Executor;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;

import static cn.hippo4j.common.constant.Constants.AVAILABLE_PROCESSORS;
import static cn.hippo4j.example.core.constant.GlobalTestConstant.MESSAGE_CONSUME;
import static cn.hippo4j.example.core.constant.GlobalTestConstant.MESSAGE_PRODUCE;

/**
 * Dynamic thread-pool config.
 */
@Slf4j
@Configuration
public class DynamicThreadPoolConfig {

    public static final ThreadPoolExecutor FIELD1 = new ThreadPoolExecutor(10, 20,
            1000, TimeUnit.MILLISECONDS, new ArrayBlockingQueue<>(20));

    public static final ThreadPoolExecutor FIELD2 = new ThreadPoolExecutor(10, 20,
            1000, TimeUnit.MILLISECONDS, new ArrayBlockingQueue<>(20));

    private static final long EXECUTE_TIMEOUT = 800L;

    private static final long AWAIT_TERMINATION_MILLIS = 5000L;

    private static final int MAX_QUEUE_CAPACITY = 200;

    private static final int CORE_POOL_SIZE = AVAILABLE_PROCESSORS * 2;

    private static final int MAX_POOL_SIZE = AVAILABLE_PROCESSORS * 4;

    /**
     * 我定义了一个 SpringBoot 的配置类，在配置类中定义了一个 DynamicThreadPoolExecutor 对象交给了 SpringBoot 容器来管理，
     * 并且还在该对象上面添加了 @DynamicThreadPool 注解。这样一来，我肯定就可以在
     * DynamicThreadPoolPostProcessor 对象处理器的 postProcessAfterInitialization
     * 方法中得到该对象，然后判断该对象是否为动态线程池对象，如果是的话，就可以把这个动态线程池的信息注册到服务端
     * @return
     */
    @Bean
    @DynamicThreadPool
    public Executor messageConsumeTtlDynamicThreadPool() {
        String threadPoolId = MESSAGE_CONSUME;
        ThreadPoolExecutor customExecutor = ThreadPoolBuilder.builder()
                .dynamicPool()
                .threadFactory(threadPoolId)
                .threadPoolId(threadPoolId)
                .executeTimeOut(EXECUTE_TIMEOUT)
                .waitForTasksToCompleteOnShutdown(true)
                .awaitTerminationMillis(AWAIT_TERMINATION_MILLIS)
                .taskDecorator(new TaskTraceBuilderHandler())
                .build();
        // Ali ttl adaptation use case.
        Executor ttlExecutor = TtlExecutors.getTtlExecutor(customExecutor);
        return ttlExecutor;
    }

    @Bean
    @DynamicThreadPool
    public Executor messageConsumeTestDynamicThreadPool() {
        String threadPoolId = "test";
        ThreadPoolExecutor customExecutor = ThreadPoolBuilder.builder()
                .dynamicPool()
                .threadFactory(threadPoolId)
                .threadPoolId(threadPoolId)
                .executeTimeOut(EXECUTE_TIMEOUT)
                .waitForTasksToCompleteOnShutdown(true)
                .awaitTerminationMillis(AWAIT_TERMINATION_MILLIS)
                .taskDecorator(new TaskTraceBuilderHandler())
                .build();
        return customExecutor;
    }

    /**
     * {@link Bean @Bean} and {@link DynamicThreadPool @DynamicThreadPool}.
     */
    @SpringDynamicThreadPool
    public ThreadPoolExecutor messageProduceDynamicThreadPool() {
        return ThreadPoolBuilder.buildDynamicPoolById(MESSAGE_PRODUCE);
    }

    /**
     * Test spring {@link ThreadPoolTaskExecutor}, Thread-pool id: testSpringThreadPoolTaskExecutor
     *
     * @return
     */
    // @Bean
    @DynamicThreadPool
    public ThreadPoolTaskExecutor testSpringThreadPoolTaskExecutor() {
        ThreadPoolTaskExecutor threadPoolTaskExecutor = new ThreadPoolTaskExecutor();
        threadPoolTaskExecutor.setThreadNamePrefix("test-spring-task-executor_");
        threadPoolTaskExecutor.setCorePoolSize(CORE_POOL_SIZE);
        threadPoolTaskExecutor.setMaxPoolSize(MAX_POOL_SIZE);
        threadPoolTaskExecutor.setQueueCapacity(MAX_QUEUE_CAPACITY);
        threadPoolTaskExecutor.setRejectedExecutionHandler(new ThreadPoolExecutor.CallerRunsPolicy());
        threadPoolTaskExecutor.setTaskDecorator(new TaskDecoratorTest.ContextCopyingDecorator());
        return threadPoolTaskExecutor;
    }
}
