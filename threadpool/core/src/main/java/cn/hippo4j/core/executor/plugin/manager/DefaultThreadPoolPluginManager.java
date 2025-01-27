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

import cn.hippo4j.common.toolkit.Assert;
import cn.hippo4j.core.executor.plugin.TaskAwarePlugin;
import cn.hippo4j.core.executor.plugin.ExecuteAwarePlugin;
import cn.hippo4j.core.executor.plugin.RejectedAwarePlugin;
import cn.hippo4j.core.executor.plugin.ShutdownAwarePlugin;
import cn.hippo4j.core.executor.plugin.ThreadPoolPlugin;
import lombok.Getter;
import lombok.NonNull;
import lombok.RequiredArgsConstructor;
import org.checkerframework.checker.nullness.qual.Nullable;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.locks.Lock;
import java.util.concurrent.locks.ReadWriteLock;
import java.util.concurrent.locks.ReentrantReadWriteLock;
import java.util.function.Consumer;
import java.util.function.Supplier;
import java.util.stream.Collectors;

/**
 * <p>The default implementation of {@link ThreadPoolPluginManager}.
 * Provide basic {@link ThreadPoolPlugin} registration, logout and acquisition functions.
 * Most APIs ensure limited thread-safe.
 *
 * <p>Usually registered to {@link cn.hippo4j.core.executor.ExtensibleThreadPoolExecutor},
 * or bound to an {@link java.util.concurrent.ThreadPoolExecutor} instance through {@link ThreadPoolPluginSupport}
 * to support its plugin based extension functions.
 *
 * <h3>Order of plugin</h3>
 * <p>By default, plugins are installed in the order in which they are registered.
 * When {@link #isEnableSort()} is true, plugins can be obtained in batches
 * in the order specified by {@link #pluginComparator}(if not null).<br />
 * When the sorting function is enabled through {@link #setPluginComparator} for the first time,
 * all registered plugins will be sorted,
 * Later, whenever a new plug-in is registered, all plug-ins will be reordered again.
 *
 * <h3>Status of the plugin</h3>
 * <p>The plugins registered in the container can be divided into two states: <em>enabled</em> and <em>disabled</em>，
 * Plugins that are <em>disabled</em> cannot be obtained through the following methods：
 * <ul>
 *     <li>{@link #getTaskAwarePluginList}</li>
 *     <li>{@link #getExecuteAwarePluginList}</li>
 *     <li>{@link #getRejectedAwarePluginList}</li>
 *     <li>{@link #getShutdownAwarePluginList}</li>
 * </ul>
 * Generally, plugins in disabled status will not be used by {@link ThreadPoolPluginSupport}.
 * users can switch the status of plugins through {@link #enable} and {@link #disable} methods.
 *
 * <h3>Thread-safe operation support</h3>
 * <p>When the list of plugins is obtained through the {@code getXXX} method of manager, the list is not immutable.
 * This means that until actually start iterating over the list,
 * registering or unregistering plugins through the manager will affect the results of the iteration.
 * Therefore, we should try to ensure that <b>get the latest plugin list from the manager before each use</b>.
 *
 * @see cn.hippo4j.core.executor.DynamicThreadPoolExecutor
 * @see cn.hippo4j.core.executor.ExtensibleThreadPoolExecutor
 */
public class DefaultThreadPoolPluginManager implements ThreadPoolPluginManager {

    /**
     * Lock of this instance
     */
    private final ReadWriteLockSupport mainLock;

    /**
     * All registered {@link ThreadPoolPlugin}
     * 线程池的插件被注册到registeredPlugins这里
     */
    private final Map<String, ThreadPoolPlugin> registeredPlugins = new ConcurrentHashMap<>(16);

    /**
     * Disabled plugins.
     */
    private final Set<String> disabledPlugins = Collections.newSetFromMap(new ConcurrentHashMap<>(16));

    /**
     * Index of enabled {@link TaskAwarePlugin}
     */
    private final QuickIndex<TaskAwarePlugin> taskAwarePluginList = new QuickIndex<>(TaskAwarePlugin.class);

    /**
     * Index of enabled  {@link ExecuteAwarePlugin}
     */
    private final QuickIndex<ExecuteAwarePlugin> executeAwarePluginList = new QuickIndex<>(ExecuteAwarePlugin.class);

    /**
     * Index of enabled  {@link RejectedAwarePlugin}
     */
    private final QuickIndex<RejectedAwarePlugin> rejectedAwarePluginList = new QuickIndex<>(RejectedAwarePlugin.class);

    /**
     * Index of enabled  {@link ShutdownAwarePlugin}
     */
    private final QuickIndex<ShutdownAwarePlugin> shutdownAwarePluginList = new QuickIndex<>(ShutdownAwarePlugin.class);

    /**
     * Comparator of {@link ThreadPoolPlugin}.
     */
    private Comparator<Object> pluginComparator;

    /**
     * Create a {@link DefaultThreadPoolPluginManager},
     * By default, plugins are not sorted,
     * and thread safety is implemented based on {@link ReentrantReadWriteLock}.
     */
    public DefaultThreadPoolPluginManager() {
        this(new ReentrantReadWriteLock(), null);
    }

    /**
     * Create a {@link DefaultThreadPoolPluginManager}.
     *
     * @param mainLock         main lock
     * @param pluginComparator comparator of plugin
     */
    public DefaultThreadPoolPluginManager(
                                          @NonNull ReadWriteLock mainLock, @Nullable Comparator<Object> pluginComparator) {
        this.pluginComparator = pluginComparator;
        this.mainLock = new ReadWriteLockSupport(mainLock);
    }

    /**
     * Clear all.
     */
    @Override
    public void clear() {
        mainLock.runWithWriteLock(() -> {
            Collection<ThreadPoolPlugin> plugins = new ArrayList<>(registeredPlugins.values());
            registeredPlugins.clear();
            forQuickIndexes(QuickIndex::clear);
            plugins.forEach(ThreadPoolPlugin::stop);
        });
    }

    /**
     * Register a {@link ThreadPoolPlugin}
     *
     * @param plugin plugin
     * @throws IllegalArgumentException thrown when a plugin with the same {@link ThreadPoolPlugin#getId()} already exists in the registry
     * @see ThreadPoolPlugin#getId()
     * @see #isEnableSort
     */
    @Override
    public void register(@NonNull ThreadPoolPlugin plugin) {
        mainLock.runWithWriteLock(() -> {
            String id = plugin.getId();
            Assert.isTrue(!isRegistered(id), "The plugin with id [" + id + "] has been registered");
            registeredPlugins.put(id, plugin);
            forQuickIndexes(quickIndex -> quickIndex.addIfPossible(plugin));
            plugin.start();
        });
    }

    /**
     * Register plugin if it's not registered.
     *
     * @param plugin plugin
     * @return return true if successful register new plugin, false otherwise
     */
    @Override
    public boolean tryRegister(ThreadPoolPlugin plugin) {
        return mainLock.applyWithWriteLock(() -> {
            if (registeredPlugins.containsKey(plugin.getId())) {
                return false;
            }
            register(plugin);
            return true;
        });
    }

    /**
     * Unregister {@link ThreadPoolPlugin}
     *
     * @param pluginId plugin id
     */
    @Override
    public void unregister(String pluginId) {
        mainLock.runWithWriteLock(
                () -> Optional.ofNullable(pluginId)
                        .map(registeredPlugins::remove)
                        .ifPresent(plugin -> {
                            disabledPlugins.remove(pluginId);
                            forQuickIndexes(quickIndex -> quickIndex.removeIfPossible(plugin));
                            plugin.stop();
                        }));
    }

    /**
     * Get id of disabled plugins.
     *
     * @return id of disabled plugins
     */
    @Override
    public Set<String> getAllDisabledPluginIds() {
        return disabledPlugins;
    }

    /**
     * Whether the plugin has been disabled.
     *
     * @param pluginId plugin id
     * @return true if plugin has been disabled, false otherwise
     */
    @Override
    public boolean isDisabled(String pluginId) {
        return disabledPlugins.contains(pluginId);
    }

    /**
     * Enable plugin, make plugins will allow access through quick indexes.
     *
     * @param pluginId plugin id
     * @return true if plugin already registered or enabled, false otherwise
     */
    @Override
    public boolean enable(String pluginId) {
        return mainLock.applyWithReadLock(() -> {
            ThreadPoolPlugin plugin = registeredPlugins.get(pluginId);
            if (Objects.isNull(plugin) || !disabledPlugins.remove(pluginId)) {
                return false;
            }
            forQuickIndexes(quickIndex -> quickIndex.addIfPossible(plugin));
            return true;
        });
    }

    /**
     * Disable plugin, make plugins will not allow access through quick indexes.
     *
     * @param pluginId plugin id
     * @return true if plugin already registered or disabled, false otherwise
     */
    @Override
    public boolean disable(String pluginId) {
        return mainLock.applyWithReadLock(() -> {
            ThreadPoolPlugin plugin = registeredPlugins.get(pluginId);
            if (Objects.isNull(plugin) || !disabledPlugins.add(pluginId)) {
                return false;
            }
            forQuickIndexes(quickIndex -> quickIndex.removeIfPossible(plugin));
            return true;
        });
    }

    /**
     * Get all registered plugins.
     *
     * @return plugins
     * @apiNote Be sure to avoid directly modifying returned collection instances,
     * otherwise, unexpected results may be obtained through the manager
     */
    @Override
    public Collection<ThreadPoolPlugin> getAllPlugins() {
        return mainLock.applyWithReadLock(() -> {
            // sort if necessary
            if (isEnableSort()) {
                return registeredPlugins.values().stream()
                        .sorted(pluginComparator)
                        .collect(Collectors.toList());
            }
            return registeredPlugins.values();
        });
    }

    /**
     * Whether the {@link ThreadPoolPlugin} has been registered.
     *
     * @param pluginId plugin id
     * @return ture if target has been registered, false otherwise
     */
    @Override
    public boolean isRegistered(String pluginId) {
        return mainLock.applyWithReadLock(() -> registeredPlugins.containsKey(pluginId));
    }

    /**
     * Get {@link ThreadPoolPlugin}.
     *
     * @param pluginId plugin id
     * @param <A>      plugin type
     * @return {@link ThreadPoolPlugin}, null if unregister
     */
    @Override
    @SuppressWarnings("unchecked")
    public <A extends ThreadPoolPlugin> Optional<A> getPlugin(String pluginId) {
        return mainLock.applyWithReadLock(
                () -> (Optional<A>) Optional.ofNullable(registeredPlugins.get(pluginId)));
    }

    /**
     * Get all enabled {@link ExecuteAwarePlugin}.
     *
     * @return {@link ExecuteAwarePlugin}
     * @apiNote Be sure to avoid directly modifying returned collection instances,
     * otherwise, unexpected results may be obtained through the manager
     * @see #enable
     * @see #disable
     */
    @Override
    public Collection<ExecuteAwarePlugin> getExecuteAwarePluginList() {
        return mainLock.applyWithReadLock(executeAwarePluginList::getPlugins);
    }

    /**
     * Get all enabled {@link RejectedAwarePlugin}.
     *
     * @return {@link RejectedAwarePlugin}
     * @apiNote Be sure to avoid directly modifying returned collection instances,
     * otherwise, unexpected results may be obtained through the manager
     * @see #enable
     * @see #disable
     */
    @Override
    public Collection<RejectedAwarePlugin> getRejectedAwarePluginList() {
        return mainLock.applyWithReadLock(rejectedAwarePluginList::getPlugins);
    }

    /**
     * Get all enabled {@link ShutdownAwarePlugin}.
     *
     * @return {@link ShutdownAwarePlugin}
     * @apiNote Be sure to avoid directly modifying returned collection instances,
     * otherwise, unexpected results may be obtained through the manager
     * @see #enable
     * @see #disable
     */
    @Override
    public Collection<ShutdownAwarePlugin> getShutdownAwarePluginList() {
        return mainLock.applyWithReadLock(shutdownAwarePluginList::getPlugins);
    }

    /**
     * Get all enabled {@link TaskAwarePlugin}.
     *
     * @return {@link TaskAwarePlugin}
     * @apiNote Be sure to avoid directly modifying returned collection instances,
     * otherwise, unexpected results may be obtained through the manager
     * @see #enable
     * @see #disable
     */
    @Override
    public Collection<TaskAwarePlugin> getTaskAwarePluginList() {
        return mainLock.applyWithReadLock(taskAwarePluginList::getPlugins);
    }

    /**
     * Whether sorting plugins is allowed.
     *
     * @return true if sorting plugins is allowed, false otherwise
     */
    public boolean isEnableSort() {
        return Objects.nonNull(pluginComparator);
    }

    /**
     * <p>Set whether sorting is allowed. <br />
     * <b>NOTE</b>:
     * If {@link #isEnableSort} returns false and {@code enableSort} is true,
     * All currently registered plug-ins will be reordered immediately.
     *
     * @param comparator comparator of plugins
     * @return {@link DefaultThreadPoolPluginManager}
     */
    public DefaultThreadPoolPluginManager setPluginComparator(@NonNull Comparator<Object> comparator) {
        mainLock.runWithWriteLock(() -> {
            // the specified comparator has been set
            if (Objects.equals(this.pluginComparator, comparator)) {
                return;
            }
            this.pluginComparator = comparator;
            forQuickIndexes(QuickIndex::sort);
        });
        return this;
    }

    /**
     * operate for each indexes
     */
    private void forQuickIndexes(Consumer<QuickIndex<? extends ThreadPoolPlugin>> consumer) {
        consumer.accept(taskAwarePluginList);
        consumer.accept(executeAwarePluginList);
        consumer.accept(rejectedAwarePluginList);
        consumer.accept(shutdownAwarePluginList);
    }

    /**
     * Quick index of registered {@link ThreadPoolPlugin}
     *
     * @param <T> plugin type
     */
    @RequiredArgsConstructor
    private class QuickIndex<T extends ThreadPoolPlugin> {

        /**
         * Plugin type
         */
        private final Class<T> pluginType;

        /**
         * Plugins
         */
        @Getter
        private final List<T> plugins = new CopyOnWriteArrayList<>();

        /**
         * Add plugin if possible.
         *
         * @param plugin plugin
         */
        public void addIfPossible(ThreadPoolPlugin plugin) {
            if (!pluginType.isInstance(plugin)) {
                return;
            }
            plugins.add(pluginType.cast(plugin));
            sort();
        }

        /**
         * Remove plugin if possible.
         *
         * @param plugin plugin
         */
        public void removeIfPossible(ThreadPoolPlugin plugin) {
            if (!pluginType.isInstance(plugin)) {
                return;
            }
            plugins.remove(pluginType.cast(plugin));
            sort();
        }

        /**
         * Sort by {@link #pluginComparator}.
         */
        public void sort() {
            if (isEnableSort()) {
                plugins.sort(pluginComparator);
            }
        }

        /**
         * Clear all.
         */
        public void clear() {
            plugins.clear();
        }

    }

    /**
     * Read write lock support.
     */
    @RequiredArgsConstructor
    private static class ReadWriteLockSupport {

        /**
         * lock
         */
        private final ReadWriteLock lock;

        /**
         * Get the read-lock and do something.
         *
         * @param supplier supplier
         */
        public <T> T applyWithReadLock(Supplier<T> supplier) {
            Lock readLock = lock.readLock();
            readLock.lock();
            try {
                return supplier.get();
            } finally {
                readLock.unlock();
            }
        }

        /**
         * Get the write-lock and do something.
         *
         * @param runnable runnable
         */
        public void runWithWriteLock(Runnable runnable) {
            Lock writeLock = lock.writeLock();
            writeLock.lock();
            try {
                runnable.run();
            } finally {
                writeLock.unlock();
            }
        }

        /**
         * Get the write-lock and do something.
         *
         * @param supplier supplier
         */
        public <T> T applyWithWriteLock(Supplier<T> supplier) {
            Lock writeLock = lock.writeLock();
            writeLock.lock();
            try {
                return supplier.get();
            } finally {
                writeLock.unlock();
            }
        }

    }

}
