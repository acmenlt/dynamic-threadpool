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

package cn.hippo4j.core.executor.support.adpter;

import cn.hippo4j.common.api.DynamicThreadPoolAdapter;
import cn.hippo4j.common.extension.spi.ServiceLoaderRegistry;
import cn.hippo4j.common.toolkit.CollectionUtil;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.Executor;
import java.util.concurrent.ThreadPoolExecutor;

/**
 * Dynamic thread pool adapter choose.
 * 动态线程池适配器选择类
 */
public class DynamicThreadPoolAdapterChoose {

    // 存储了所有执行器适配器对象的集合
    private static final List<DynamicThreadPoolAdapter> DYNAMIC_THREAD_POOL_ADAPTERS = new ArrayList<>();

    // 添加三个动态线程池适配器对象
    static {
        DYNAMIC_THREAD_POOL_ADAPTERS.add(new TransmittableThreadLocalExecutorAdapter());
        DYNAMIC_THREAD_POOL_ADAPTERS.add(new TransmittableThreadLocalExecutorServiceAdapter());
        DYNAMIC_THREAD_POOL_ADAPTERS.add(new ThreadPoolTaskExecutorAdapter());
        DYNAMIC_THREAD_POOL_ADAPTERS.add(new ZipkinExecutorAdapter());
        loadCustomerAdapter();
    }

    /**
     * Check if the object contains thread pool information.
     *
     * @param executor objects where there may be instances
     *                 of dynamic thread pools
     * @return matching results
     * //匹配执行器的适配器对象
     */
    public static boolean match(Object executor) {
        return DYNAMIC_THREAD_POOL_ADAPTERS.stream().anyMatch(each -> each.match(executor));
    }

    /**
     * Get the dynamic thread pool reference in the object.
     *
     * @param executor objects where there may be instances
     *                 of dynamic thread pools
     * @return get the real dynamic thread pool instance
     * //使用执行器的适配器对象得到执行器中的动态线程池
     */
    public static ThreadPoolExecutor unwrap(Object executor) {
        Optional<DynamicThreadPoolAdapter> dynamicThreadPoolAdapterOptional = DYNAMIC_THREAD_POOL_ADAPTERS.stream().filter(each -> each.match(executor)).findFirst();
        return dynamicThreadPoolAdapterOptional.map(each -> each.unwrap(executor)).orElse(null);
    }

    /**
     * If the {@link DynamicThreadPoolAdapter#match(Object)} conditions are met,
     * the thread pool is replaced with a dynamic thread pool.
     *
     * @param executor                  objects where there may be instances
     *                                  of dynamic thread pools
     * @param dynamicThreadPoolExecutor dynamic thread-pool executor
     *                                  //使用dynamicThreadPoolExecutor替代executor中的线程池成员变量
     */
    public static void replace(Object executor, Executor dynamicThreadPoolExecutor) {
        Optional<DynamicThreadPoolAdapter> dynamicThreadPoolAdapterOptional = DYNAMIC_THREAD_POOL_ADAPTERS.stream().filter(each -> each.match(executor)).findFirst();
        dynamicThreadPoolAdapterOptional.ifPresent(dynamicThreadPoolAdapter -> dynamicThreadPoolAdapter.replace(executor, dynamicThreadPoolExecutor));
    }

    /**
     * Load SPI customer adapter.
     */
    private static void loadCustomerAdapter() {
        ServiceLoaderRegistry.register(DynamicThreadPoolAdapter.class);
        Collection<DynamicThreadPoolAdapter> instances = ServiceLoaderRegistry.getSingletonServiceInstances(DynamicThreadPoolAdapter.class);
        if (CollectionUtil.isEmpty(instances)) {
            return;
        }
        for (DynamicThreadPoolAdapter instance : instances) {
            if (instance != null) {
                DYNAMIC_THREAD_POOL_ADAPTERS.add(instance);
            }
        }
    }
}
