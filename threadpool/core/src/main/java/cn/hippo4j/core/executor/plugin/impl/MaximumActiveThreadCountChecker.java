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

package cn.hippo4j.core.executor.plugin.impl;

import cn.hippo4j.core.executor.plugin.ExecuteAwarePlugin;
import cn.hippo4j.core.executor.plugin.PluginRuntime;
import cn.hippo4j.core.executor.plugin.manager.MaximumActiveThreadCountCheckerRegistrar;
import cn.hippo4j.core.executor.plugin.manager.ThreadPoolPluginSupport;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

import java.util.concurrent.RunnableFuture;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;

/**
 * <p>A plugin that checks whether the number of active threads in a thread pool exceeds the maximum thread count.
 *
 * <p>When task is submitted to the thread pool, before the current worker thread executes the task:
 * <ol>
 *     <li>check whether the maximum number of threads in the thread pool is exceeded;</li>
 *     <li>
 *         if already exceeded, interrupt the worker thread,
 *         and throw an {@link IllegalMaximumActiveCountException} exception to destroy the worker thread;
 *     </li>
 * </ol>
 *
 * <p><b>NOTE</b>: if custom {@link Thread.UncaughtExceptionHandler} is set for the thread pool,
 * it may catch the {@link IllegalMaximumActiveCountException} exception and cause the worker thread to not be destroyed.
 *
 * @see MaximumActiveThreadCountCheckerRegistrar
 * @see IllegalMaximumActiveCountException
 */
@RequiredArgsConstructor
@Slf4j
public class MaximumActiveThreadCountChecker implements ExecuteAwarePlugin {

    /**
     * Thread pool.
     */
    public final ThreadPoolPluginSupport threadPoolPluginSupport;

    /**
     * <p>A pointer to a number object used to record the number of threads in the thread pool
     * that exceeds the maximum thread count.
     * There are two possible scenarios:
     * <ul>
     *     <li>Pointing to {@code null}: indicating that there are no threads in the thread pool exceeding the maximum count;</li>
     *     <li>
     *         Pointing to a number object, indicating that there are threads in the thread pool
     *         exceeding the maximum count, and the number represents the overflow thread count;
     *     </li>
     * </ul>
     *
     * <p>After performing operations based on the original number object pointed by the pointer,
     * the pointer may change:
     * <ul>
     *     <li>The pointer remains unchanged, indicating the operations performed on the original number object are valid;</li>
     *     <li>
     *         The pointer points to another number object, indicating that the operations performed
     *         on the original number object should be considered invalid.
     *         It requires obtaining the number object again and retrying the operations
     *     </li>
     *     <li>
     *         The pointer points to {@code null}, indicating that there are no threads
     *         in the thread pool exceeding the maximum count.
     *         The operations based on the old reference are still effective;
     *     </li>
     * </ul>
     */
    public final AtomicReference<AtomicInteger> overflowThreadNumber = new AtomicReference<>(null);

    /**
     * <p>Set the number of overflow threads.
     *
     * @param number number of overflow threads
     */
    public void setOverflowThreadNumber(int number) {
        AtomicInteger currentOverflowThreads = (number > 0) ? new AtomicInteger(number) : null;
        overflowThreadNumber.set(currentOverflowThreads);
    }

    /**
     * <p>Get the number of overflow threads.
     */
    public Integer getOverflowThreadNumber() {
        AtomicInteger currentOverflowThreads = overflowThreadNumber.get();
        return currentOverflowThreads == null ? 0 : currentOverflowThreads.get();
    }

    /**
     * <p>Check {@link ThreadPoolExecutor#getActiveCount()} whether greater than {@link ThreadPoolExecutor#getMaximumPoolSize()}.
     * if the number of threads in the thread pool exceeds the maximum thread count, set the number of overflow threads.
     *
     * @return number of overflow threads
     * @see #setOverflowThreadNumber(int)
     */
    public Integer checkOverflowThreads() {
        ThreadPoolExecutor executor = threadPoolPluginSupport.getThreadPoolExecutor();
        int activeCount = executor.getActiveCount();
        int maximumActiveCount = executor.getMaximumPoolSize();
        int number = activeCount > maximumActiveCount ? activeCount - maximumActiveCount : 0;
        if (number > 0) {
            setOverflowThreadNumber(number);
        }
        return number;
    }

    /**
     * Get plugin runtime info.
     *
     * @return plugin runtime info
     */
    @Override
    public PluginRuntime getPluginRuntime() {
        return new PluginRuntime(getId())
            .addInfo("overflowThreadNumber", getOverflowThreadNumber());
    }

    /**
     * <p>Check the maximum number of threads in the thread pool before the task is executed,
     * if the maximum number of threads is exceeded,
     * an {@link IllegalMaximumActiveCountException} will be thrown.
     *
     * @param thread   thread of executing task
     * @param runnable task
     * @throws IllegalMaximumActiveCountException thread if the maximum number of threads is exceeded
     * @see ThreadPoolExecutor#beforeExecute
     */
    @Override
    public void beforeExecute(Thread thread, Runnable runnable) {
        if (!hasOverflowThread()) {
            return;
        }
        log.warn(
                "The maximum number of threads in the thread pool '{}' has been exceeded, task '{}' will redelivery",
                threadPoolPluginSupport.getThreadPoolId(), runnable);
        submitTaskAfterCheckFail(runnable);
        interruptAndThrowException(thread);
    }

    /**
     * <p>check the maximum number of threads in the thread pool after the task is executed.
     *
     * @return true if the maximum number of threads in the thread pool has been exceeded
     */
    @SuppressWarnings("all")
    private boolean hasOverflowThread() {
        AtomicInteger current;

        // has overflow thread?
        while ((current = overflowThreadNumber.get()) != null) {
            int curr = current.get();

            // it's already no overflow thread
            if (curr < 1) {
                // check whether the current value is effective, if not, try again
                if (overflowThreadNumber.compareAndSet(current, null)) {
                    return false;
                }
                continue;
            }

            // has overflow thread, try to cas to decrease the number of overflow threads
            int expect = curr - 1;
            if (!current.compareAndSet(curr, expect)) {
                // cas fail, the current value is uneffective, try again
                continue;
            }

            /*
             cas success, decrease the number of overflow threads,
             then, use cas to check the current value is effective:

             1.if already no overflow thread, set the overflowThreadNumber to null;
             2.if still has other overflow thread, keep the overflowThreadNumber reference unchanged
             */
            AtomicInteger newRef = (expect > 0) ? current : null;
            if (overflowThreadNumber.compareAndSet(current, newRef)) {

                /*
                 cas success, it's means the current value is effective.

                 if this thread is the last overflow thread,
                 and already set the overflowThreadNumber to null,
                 then we return true to throw an exception.
                 */
                if (expect == 0) {
                    return true;
                }

                /*
                 if this thread is not the last overflow thread,
                 the value may still be decreased by other threads before this step.

                 1.if current value still greater than 0,
                 we must try again to check whether the current value is effective.

                 2.if current value is set to null,
                 it's means that after the current value is decremented to 0 by other threads, it is set to null.

                 in addition, here may be a ABA problem:
                 when cas success, if the current value is still greater than 0,
                 its means still has other overflow thread can set it to null after the value is cas to 0,
                 we can't determine whether the null value is set by same check,
                 so we may make an error and throw an exception in the next check after the current check.
                 it's low probability, and the error is acceptable.
                 */
                AtomicInteger nc = overflowThreadNumber.get();
                if (nc == current || nc == null) {
                    return true;
                }
            }
            // the current value is uneffective, try again
        }

        // no overflow thread
        return false;
    }

    /**
     * <p>Submit task to thread pool after check fail.
     *
     * @param runnable task
     */
    protected void submitTaskAfterCheckFail(Runnable runnable) {
        if (runnable instanceof RunnableFuture) {
            RunnableFuture<?> future = (RunnableFuture<?>) runnable;
            if (future.isDone()) {
                return;
            }
            if (future.isCancelled()) {
                return;
            }
        }
        threadPoolPluginSupport.getThreadPoolExecutor().execute(runnable);
    }

    private void interruptAndThrowException(Thread thread) {
        thread.interrupt();
        throw new IllegalMaximumActiveCountException(
                "The maximum number of threads in the thread pool '" + threadPoolPluginSupport.getThreadPoolId()
                        + "' has been exceeded.");
    }

    /**
     * A {@link RuntimeException} that indicates that the maximum number of threads in the thread pool has been exceeded.
     */
    public static class IllegalMaximumActiveCountException extends RuntimeException {

        /**
         * Constructs a new runtime exception with the specified detail message.
         * The cause is not initialized, and may subsequently be initialized by a
         * call to {@link #initCause}.
         *
         * @param message the detail message. The detail message is saved for
         *                later retrieval by the {@link #getMessage()} method.
         */
        public IllegalMaximumActiveCountException(String message) {
            super(message);
        }
    }
}
