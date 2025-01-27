package cn.hippo4j.core.executor.plugin.manager;

import cn.hippo4j.common.toolkit.Assert;
import cn.hippo4j.core.executor.plugin.impl.MaximumActiveThreadCountChecker;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;

/**
 * <p>A register for {@link MaximumActiveThreadCountChecker}.<br />
 * After {@link #startCheckOverflowThreads} has been invoked,
 * it will start a task for check whether the thread pool has overflow threads in the specified interval.
 *
 * @author huangchengxing
 * @see MaximumActiveThreadCountChecker
 */
@Slf4j
@RequiredArgsConstructor
public class MaximumActiveThreadCountCheckerRegistrar implements ThreadPoolPluginRegistrar {

    /**
     * registered checkers
     */
    private final List<MaximumActiveThreadCountChecker> checkers = new CopyOnWriteArrayList<>();

    /**
     * scheduled executor service
     */
    private final ScheduledExecutorService scheduledExecutorService;

    /**
     * task for check whether the thread pool has overflow threads.
     */
    private ScheduledFuture<?> task;

    /**
     * <p>Start task for check whether the thread pool has overflow threads.
     * if task is already running, it will be canceled and restarted.
     */
    public void startCheckOverflowThreads(long checkInterval, TimeUnit checkIntervalTimeUnit) {
        Assert.isTrue(checkInterval > 0, "checkInterval must be greater than 0");
        Assert.notNull(checkIntervalTimeUnit, "checkIntervalTimeUnit must not be null");
        if (task != null) {
            stopCheckOverflowThreads();
        }
        log.info("start check overflow threads task, checkInterval: {}, checkIntervalTimeUnit: {}", checkInterval, checkIntervalTimeUnit);
        this.task = scheduledExecutorService.scheduleAtFixedRate(
            () -> checkers.forEach(MaximumActiveThreadCountChecker::checkOverflowThreads),
            checkInterval, checkInterval, checkIntervalTimeUnit
        );
    }

    /**
     * Cancel task for check whether the thread pool has overflow threads.
     *
     * @return true if the task was canceled before it completed normally
     */
    public boolean stopCheckOverflowThreads() {
        // is acceptable to cancel a task even if it is already running,
        // because this behavior will not affect the normal operation of the thread pool
        if (task != null && task.cancel(true)) {
            log.info("cancel check overflow threads task");
            return true;
        }
        return false;
    }

    /**
     * Create and register plugin for the specified thread-pool instance.
     *
     * @param support thread pool plugin manager delegate
     */
    @Override
    public void doRegister(ThreadPoolPluginSupport support) {
        MaximumActiveThreadCountChecker checker = new MaximumActiveThreadCountChecker(support);
        if (log.isDebugEnabled()) {
            log.debug("register maximum active thread count checker for thread pool: {}", support.getThreadPoolId());
        }
        support.register(checker);
        checkers.add(checker);
    }
}
