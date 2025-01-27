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

package cn.hippo4j.core.executor.plugin.manager;

import cn.hippo4j.core.executor.plugin.ThreadPoolPlugin;
import cn.hippo4j.core.executor.plugin.impl.TaskDecoratorPlugin;
import cn.hippo4j.core.executor.plugin.impl.TaskRejectCountRecordPlugin;
import cn.hippo4j.core.executor.plugin.impl.TaskRejectNotifyAlarmPlugin;
import cn.hippo4j.core.executor.plugin.impl.TaskTimeoutNotifyAlarmPlugin;
import cn.hippo4j.core.executor.plugin.impl.ThreadPoolExecutorShutdownPlugin;
import lombok.AllArgsConstructor;
import lombok.NoArgsConstructor;

/**
 * Register default {@link ThreadPoolPlugin}.
 *
 * @see TaskDecoratorPlugin
 * @see TaskTimeoutNotifyAlarmPlugin
 * @see TaskRejectCountRecordPlugin
 * @see TaskRejectNotifyAlarmPlugin
 * @see ThreadPoolExecutorShutdownPlugin
 */
// 默认的线程池插件注册器组件，通过这个注册器对象，把线程池的所有插件都注册到线程池的插件管理器中
// DefaultThreadPoolPluginManager是一个线程池插件管理器，它的作用是管理线程池的插件
// 这个组件在DynamicThreadPoolExecutor对象的构造方法中被创建，然后开始将插件注册到插件管理器中
@NoArgsConstructor
@AllArgsConstructor
public class DefaultThreadPoolPluginRegistrar implements ThreadPoolPluginRegistrar {

    /**
     * Execute time out
     * //这个就是用户设置的任务执行的超时时间
     */
    private long executeTimeOut;

    /**
     * Await termination millis
     * //线程池关闭时，等待剩余任务执行的最大时间
     */
    private long awaitTerminationMillis;

    /**
     * Create and register plugin for the specified thread-pool instance.
     *
     * @param support thread pool plugin manager delegate
     */
    //这个就是把动态线程池各个内置的功能扩展插件注册到动态线程池的内部的插件管理器成员变量中的方法
    @Override
    public void doRegister(ThreadPoolPluginSupport support) {
        //这里调用了DynamicThreadPoolExecutor对象的register方法，但是在我们目前的程序中
        //动态线程池中根本没有register方法，所以还需要在动态线程池中再添加一个register方法
        //具体实现我就写在当前这个代码块中了，请大家继续向下看
        support.register(new TaskDecoratorPlugin());
        support.register(new TaskTimeoutNotifyAlarmPlugin(support.getThreadPoolId(), executeTimeOut, support.getThreadPoolExecutor()));
        support.register(new TaskRejectCountRecordPlugin());
        support.register(new TaskRejectNotifyAlarmPlugin());
        //awaitTerminationMillis就是等待剩余任务执行的最大时间，这两个参数是怎么被赋值的呢？
        support.register(new ThreadPoolExecutorShutdownPlugin(awaitTerminationMillis));
    }
}
