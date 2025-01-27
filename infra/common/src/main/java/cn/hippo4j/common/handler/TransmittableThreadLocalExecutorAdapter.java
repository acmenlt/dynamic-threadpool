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

package cn.hippo4j.common.handler;

import cn.hippo4j.common.api.DynamicThreadPoolAdapter;
import cn.hippo4j.common.toolkit.ReflectUtil;

import java.util.Objects;
import java.util.concurrent.Executor;
import java.util.concurrent.ThreadPoolExecutor;

/**
 * Transmittable thread local executor adapter.
 */
public class TransmittableThreadLocalExecutorAdapter implements DynamicThreadPoolAdapter {

    private static final String MATCH_CLASS_NAME = "ExecutorTtlWrapper";

    private static final String FIELD_NAME = "executor";

    // 判断传进来的对象是否和当前适配器器对象匹配
    @Override
    public boolean match(Object executor) {
        // 其实就是判断对象的类名是否为ExecutorTtlWrapper，如果是就意味着是第三方线程池
        // 这个线程池中持有者动态线程池对象
        return Objects.equals(MATCH_CLASS_NAME, executor.getClass().getSimpleName());
    }
    // 从ExecutorTtlWrapper对象中获得其持有的DynamicThreadPoolExecutor对象
    @Override
    public ThreadPoolExecutor unwrap(Object executor) {
        // 通过反射获得ExecutorTtlWrapper对象的executor成员变量
        // 在之前展示的ExecutorTtlWrapper类的代码中，可以看到，动态线程池会赋值给
        // ExecutorTtlWrapper的executor成员变量
        return (ThreadPoolExecutor) ReflectUtil.getFieldValue(executor, FIELD_NAME);
    }

    @Override
    public void replace(Object executor, Executor dynamicThreadPoolExecutor) {
        // 将dynamicThreadPoolExecutor对象替换到executor中
        ReflectUtil.setFieldValue(executor, FIELD_NAME, dynamicThreadPoolExecutor);
    }
}
