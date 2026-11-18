package com.gs5.onboarding.infrastructure.scheduling;

import net.javacrumbs.shedlock.core.LockConfiguration;
import net.javacrumbs.shedlock.core.LockingTaskExecutor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.time.Clock;
import java.time.Duration;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

/**
 * A task run with a fixed delay on its own thread, under a distributed lock (ShedLock), so that only one replica
 * of the service runs it at a time. Plain JDK scheduling: no annotations, no proxies, started and stopped explicitly.
 */
public final class PeriodicJob {

    private static final Logger log = LoggerFactory.getLogger(PeriodicJob.class);

    private final String name;
    private final Duration delay;
    private final Duration lockAtMostFor;
    private final Runnable task;
    private final LockingTaskExecutor locks;
    private final Clock clock;
    private ScheduledExecutorService executor;

    /**
     * @param name          job name - also the name of its lock, shared by all replicas
     * @param delay         pause between the end of one run and the start of the next
     * @param lockAtMostFor how long the lock is held if the replica running the job dies
     */
    public PeriodicJob(String name, Duration delay, Duration lockAtMostFor, Runnable task, LockingTaskExecutor locks,
                       Clock clock) {
        this.name = name;
        this.delay = delay;
        this.lockAtMostFor = lockAtMostFor;
        this.task = task;
        this.locks = locks;
        this.clock = clock;
    }

    public synchronized void start() {
        if (executor == null) {
            executor = Executors.newSingleThreadScheduledExecutor(Thread.ofPlatform().name(name).daemon().factory());
            executor.scheduleWithFixedDelay(this::runOnce, 0, delay.toMillis(), TimeUnit.MILLISECONDS);
        }
    }

    public synchronized void stop() {
        if (executor != null) {
            executor.shutdownNow();
            executor = null;
        }
    }

    private void runOnce() {
        try {
            locks.executeWithLock(task, new LockConfiguration(clock.instant(), name, lockAtMostFor, Duration.ZERO));
        } catch (RuntimeException e) {
            // a task that throws is silently dropped by the executor: log the failure and keep the schedule alive
            log.error("Job {} failed, next run in {}", name, delay, e);
        }
    }
}
