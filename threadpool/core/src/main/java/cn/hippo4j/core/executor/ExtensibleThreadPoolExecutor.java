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

import cn.hippo4j.core.executor.plugin.ExecuteAwarePlugin;
import cn.hippo4j.core.executor.plugin.RejectedAwarePlugin;
import cn.hippo4j.core.executor.plugin.ShutdownAwarePlugin;
import cn.hippo4j.core.executor.plugin.TaskAwarePlugin;
import cn.hippo4j.core.executor.plugin.ThreadPoolPlugin;
import cn.hippo4j.core.executor.plugin.manager.ThreadPoolPluginManager;
import cn.hippo4j.core.executor.plugin.manager.ThreadPoolPluginSupport;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NonNull;
import lombok.Setter;

import java.util.Collection;
import java.util.Collections;
import java.util.List;
import java.util.Objects;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.Callable;
import java.util.concurrent.RejectedExecutionHandler;
import java.util.concurrent.RunnableFuture;
import java.util.concurrent.ThreadFactory;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;

/**
 * <p>Extensible thread-pool executor. <br />
 * Support the callback extension points provided on the basis of {@link ThreadPoolExecutor}.
 * Each extension point corresponds to a different {@link ThreadPoolPlugin} interface,
 * users can customize plug-ins and implement one or more {@link ThreadPoolPlugin} interface
 * to enable plugins to sense thread pool behavior and provide extended functions.
 *
 * @see ThreadPoolPluginManager
 * @see ThreadPoolPlugin
 * 扩展线程池类，这个类为jdk的原生线程池提供了非常多的扩展点，基本上每一个重要操作都提供了拓展点
 * 插件体系
 */
public class ExtensibleThreadPoolExecutor extends ThreadPoolExecutor implements ThreadPoolPluginSupport {

    /**
     * Thread pool id
     * 线程池Id
     */
    @Getter
    private final String threadPoolId;

    /**
     * Action aware registry
     * 插件管理器对象，当前线程池用到的所有插件都会注册到这个管理器中
     */
    @Getter
    private final ThreadPoolPluginManager threadPoolPluginManager;

    /**
     * Handler wrapper, any changes to the current instance {@link RejectedExecutionHandler} should be made through this wrapper
     */
    private final RejectedAwareHandlerWrapper handlerWrapper;

    /**
     * Creates a new {@code ExtensibleThreadPoolExecutor} with the given initial parameters.
     *
     * @param threadPoolId            thread-pool id
     * @param threadPoolPluginManager action aware registry
     * @param corePoolSize            the number of threads to keep in the pool, even
     *                                if they are idle, unless {@code allowCoreThreadTimeOut} is set
     * @param maximumPoolSize         the maximum number of threads to allow in the
     *                                pool
     * @param keepAliveTime           when the number of threads is greater than
     *                                the core, this is the maximum time that excess idle threads
     *                                will wait for new tasks before terminating.
     * @param unit                    the time unit for the {@code keepAliveTime} argument
     * @param workQueue               the queue to use for holding tasks before they are
     *                                executed.  This queue will hold only the {@code Runnable}
     *                                tasks submitted by the {@code execute} method.
     * @param threadFactory           the factory to use when the executor
     *                                creates a new thread
     * @param handler                 the handler to use when execution is blocked
     *                                because the thread bounds and queue capacities are reached
     * @throws IllegalArgumentException if one of the following holds:<br>
     *                                  {@code corePoolSize < 0}<br>
     *                                  {@code keepAliveTime < 0}<br>
     *                                  {@code maximumPoolSize <= 0}<br>
     *                                  {@code maximumPoolSize < corePoolSize}
     * @throws NullPointerException     if {@code workQueue}
     *                                  or {@code threadFactory} or {@code handler} is null
     */
    public ExtensibleThreadPoolExecutor(
                                        @NonNull String threadPoolId,
                                        @NonNull ThreadPoolPluginManager threadPoolPluginManager,
                                        int corePoolSize, int maximumPoolSize,
                                        long keepAliveTime, TimeUnit unit,
                                        @NonNull BlockingQueue<Runnable> workQueue,
                                        @NonNull ThreadFactory threadFactory,
                                        @NonNull RejectedExecutionHandler handler) {
        super(corePoolSize, maximumPoolSize, keepAliveTime, unit, workQueue, threadFactory, handler);
        // pool extended info.
        //给线程池Id赋值
        this.threadPoolId = threadPoolId;
        this.threadPoolPluginManager = threadPoolPluginManager;
        // proxy handler to support callback, repeated packaging of the same rejection policy should be avoided here.
        this.handlerWrapper = new RejectedAwareHandlerWrapper(threadPoolPluginManager, handler);
        super.setRejectedExecutionHandler(handlerWrapper);
    }

    /**
     * {@inheritDoc}
     *
     * <p><b>Before calling the parent class method, {@link ExecuteAwarePlugin#beforeExecute} will be called first.
     *
     * @param thread   the thread that will run task {@code r}
     * @param runnable the task that will be executed
     *                执行任务之前的回调
     */
    @Override
    protected void beforeExecute(Thread thread, Runnable runnable) {
        Collection<ExecuteAwarePlugin> executeAwarePluginList = threadPoolPluginManager.getExecuteAwarePluginList();
        executeAwarePluginList.forEach(aware -> aware.beforeExecute(thread, runnable));
    }

    /**
     * {@inheritDoc}
     *
     * <p>Before calling the superclass method, {@link TaskAwarePlugin#beforeTaskExecute} will be called first. <br />
     * If the task becomes null after being processed by the {@link TaskAwarePlugin#beforeTaskExecute},
     * the task will not be submitted.
     *
     * @param runnable the task to execute
     */
    @Override
    public void execute(@NonNull Runnable runnable) {
        Collection<TaskAwarePlugin> taskAwarePluginList = threadPoolPluginManager.getTaskAwarePluginList();
        for (TaskAwarePlugin taskAwarePlugin : taskAwarePluginList) {
            runnable = taskAwarePlugin.beforeTaskExecute(runnable);
            if (Objects.isNull(runnable)) {
                return;
            }
        }
        super.execute(runnable);
    }

    /**
     * {@inheritDoc}
     *
     * <p><b>After calling the superclass method, {@link ExecuteAwarePlugin#afterExecute} will be called last.
     *
     * @param runnable  the runnable that has completed
     * @param throwable the exception that caused termination, or null if
     *                  execution completed normally
     *                  执行任务之后的回调
     */
    @Override
    protected void afterExecute(Runnable runnable, Throwable throwable) {
        Collection<ExecuteAwarePlugin> executeAwarePluginList = threadPoolPluginManager.getExecuteAwarePluginList();
        executeAwarePluginList.forEach(aware -> aware.afterExecute(runnable, throwable));
    }

    /**
     * {@inheritDoc}
     *
     * <p><b>Before calling the superclass method,
     * {@link ShutdownAwarePlugin#beforeShutdown} will be called first.
     * and then will be call {@link ShutdownAwarePlugin#afterShutdown}
     *
     * @throws SecurityException {@inheritDoc}
     */
    @Override
    public void shutdown() {
        Collection<ShutdownAwarePlugin> shutdownAwarePluginList = threadPoolPluginManager.getShutdownAwarePluginList();
        shutdownAwarePluginList.forEach(aware -> aware.beforeShutdown(this));
        super.shutdown();
        shutdownAwarePluginList.forEach(aware -> aware.afterShutdown(this, Collections.emptyList()));
    }

    /**
     * {@inheritDoc}
     *
     * <p><b>Before calling the superclass method,
     * {@link ShutdownAwarePlugin#beforeShutdown} will be called first.
     * and then will be call {@link ShutdownAwarePlugin#afterShutdown}
     *
     * @throws SecurityException
     */
    @Override
    public List<Runnable> shutdownNow() {
        Collection<ShutdownAwarePlugin> shutdownAwarePluginList = threadPoolPluginManager.getShutdownAwarePluginList();
        shutdownAwarePluginList.forEach(aware -> aware.beforeShutdown(this));
        List<Runnable> tasks = super.shutdownNow();
        shutdownAwarePluginList.forEach(aware -> aware.afterShutdown(this, tasks));
        return tasks;
    }

    /**
     * {@inheritDoc}
     *
     * <p><b>Before calling the superclass method, {@link ShutdownAwarePlugin#afterTerminated} will be called first.
     */
    @Override
    protected void terminated() {
        super.terminated();
        Collection<ShutdownAwarePlugin> shutdownAwarePluginList = threadPoolPluginManager.getShutdownAwarePluginList();
        shutdownAwarePluginList.forEach(aware -> aware.afterTerminated(this));
    }

    /**
     * {@inheritDoc}
     *
     * <p><b>Before calling the superclass method, {@link TaskAwarePlugin#beforeTaskCreate} will be called first.
     *
     * @param runnable the runnable task being wrapped
     * @param value    the default value for the returned future
     * @return a {@code RunnableFuture} which, when run, will run the
     * underlying runnable and which, as a {@code Future}, will yield
     * the given value as its result and provide for cancellation of
     * the underlying task
     * @since 1.6
     */
    @Override
    protected <T> RunnableFuture<T> newTaskFor(Runnable runnable, T value) {
        Collection<TaskAwarePlugin> taskAwarePluginList = threadPoolPluginManager.getTaskAwarePluginList();
        for (TaskAwarePlugin taskAwarePlugin : taskAwarePluginList) {
            runnable = taskAwarePlugin.beforeTaskCreate(this, runnable, value);
        }
        return super.newTaskFor(runnable, value);
    }

    /**
     * {@inheritDoc}
     *
     * <p><b>Before calling the superclass method, {@link TaskAwarePlugin#beforeTaskCreate} will be called first.
     *
     * @param callable the callable task being wrapped
     * @return a {@code RunnableFuture} which, when run, will call the
     * underlying callable and which, as a {@code Future}, will yield
     * the callable's result as its result and provide for
     * cancellation of the underlying task
     * @since 1.6
     */
    @Override
    protected <T> RunnableFuture<T> newTaskFor(Callable<T> callable) {
        Collection<TaskAwarePlugin> taskAwarePluginList = threadPoolPluginManager.getTaskAwarePluginList();
        for (TaskAwarePlugin taskAwarePlugin : taskAwarePluginList) {
            callable = taskAwarePlugin.beforeTaskCreate(this, callable);
        }
        return super.newTaskFor(callable);
    }

    /**
     * Sets a new handler for unexecutable tasks.
     *
     * @param handler the new handler
     * @throws NullPointerException if handler is null
     * @see #getRejectedExecutionHandler
     */
    @Override
    public void setRejectedExecutionHandler(@NonNull RejectedExecutionHandler handler) {
        handlerWrapper.setHandler(handler);
    }

    /**
     * Returns the current handler for unexecutable tasks.
     *
     * @return the current handler
     * @see #setRejectedExecutionHandler(RejectedExecutionHandler)
     */
    @Override
    public RejectedExecutionHandler getRejectedExecutionHandler() {
        return handlerWrapper.getHandler();
    }

    /**
     * Get thread-pool executor.
     *
     * @return thread-pool executor
     */
    @Override
    public ThreadPoolExecutor getThreadPoolExecutor() {
        return this;
    }

    /**
     * Wrapper of original {@link RejectedExecutionHandler} of {@link ThreadPoolExecutor},
     * It's used to support the {@link RejectedAwarePlugin} on the basis of the {@link RejectedExecutionHandler}.
     *
     * @see RejectedAwarePlugin
     */
    @AllArgsConstructor
    private static class RejectedAwareHandlerWrapper implements RejectedExecutionHandler {

        /**
         * Thread-pool action aware registry
         */
        private final ThreadPoolPluginManager registry;

        /**
         * Original target
         */
        @Setter
        @Getter
        private RejectedExecutionHandler handler;

        /**
         * Call {@link RejectedAwarePlugin#beforeRejectedExecution}, then reject the task.
         *
         * @param r        the runnable task requested to be executed
         * @param executor the executor attempting to execute this task
         */
        @Override
        public void rejectedExecution(Runnable r, ThreadPoolExecutor executor) {
            Collection<RejectedAwarePlugin> rejectedAwarePluginList = registry.getRejectedAwarePluginList();
            rejectedAwarePluginList.forEach(aware -> aware.beforeRejectedExecution(r, executor));
            handler.rejectedExecution(r, executor);
        }
    }
}
