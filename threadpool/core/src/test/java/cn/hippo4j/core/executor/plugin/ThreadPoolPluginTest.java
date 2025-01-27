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

package cn.hippo4j.core.executor.plugin;

import cn.hippo4j.common.toolkit.ThreadUtil;
import cn.hippo4j.core.executor.ExtensibleThreadPoolExecutor;
import cn.hippo4j.core.executor.plugin.*;
import cn.hippo4j.core.executor.plugin.manager.DefaultThreadPoolPluginManager;
import lombok.Getter;
import lombok.extern.slf4j.Slf4j;
import org.junit.Assert;
import org.junit.Test;

import java.util.List;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * test for default method of {@link ThreadPoolPlugin} and it's subclass
 */
@Slf4j
public class ThreadPoolPluginTest {

    @Test
    public void testDefaultMethod() {
        ExtensibleThreadPoolExecutor executor = new ExtensibleThreadPoolExecutor(
                "test", new DefaultThreadPoolPluginManager(),
                1, 1, 1000L, TimeUnit.MILLISECONDS,
                new ArrayBlockingQueue<>(1), Thread::new, new ThreadPoolExecutor.DiscardPolicy());

        executor.register(new TestTaskAwarePlugin());
        executor.register(new TestExecuteAwarePlugin());
        executor.register(new TestRejectedAwarePlugin());
        executor.register(new TestShutdownAwarePlugin());

        AtomicInteger count = new AtomicInteger(0);
        executor.submit(() -> {
            ThreadUtil.sleep(100L);
            return count.incrementAndGet();
        });
        executor.submit(() -> {
            ThreadUtil.sleep(100L);
            count.incrementAndGet();
        });
        executor.submit(count::incrementAndGet, 2);

        // waiting for shutdown
        executor.shutdown();
        while (!executor.isTerminated()) {
        }

        Assert.assertEquals(2, count.get());
    }

    @Getter
    private final static class TestTaskAwarePlugin implements TaskAwarePlugin {

        private final String id = this.getClass().getSimpleName();
    }

    @Getter
    private final static class TestExecuteAwarePlugin implements ExecuteAwarePlugin {

        private final String id = this.getClass().getSimpleName();
    }

    @Getter
    private final static class TestRejectedAwarePlugin implements RejectedAwarePlugin {

        private final String id = this.getClass().getSimpleName();
    }

    @Getter
    private final static class TestShutdownAwarePlugin implements ShutdownAwarePlugin {

        private final String id = this.getClass().getSimpleName();

        @Override
        public void beforeShutdown(ThreadPoolExecutor executor) {
           log.info("before shutdown", id);
        }

        @Override
        public void afterShutdown(ThreadPoolExecutor executor, List<Runnable> remainingTasks) {
            log.info("after shutdown", id);
        }
    }

}
