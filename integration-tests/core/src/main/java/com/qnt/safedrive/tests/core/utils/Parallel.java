package com.qnt.safedrive.tests.core.utils;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.Callable;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;

/** Runs calls at the same moment, for race tests (concurrent duplicates). */
public final class Parallel {

    private Parallel() {}

    /** Runs every call on its own thread, released together, and returns the results in call order. */
    public static <T> List<T> all(List<Callable<T>> calls) {
        CountDownLatch ready = new CountDownLatch(calls.size());
        CountDownLatch go = new CountDownLatch(1);
        try (ExecutorService pool = Executors.newFixedThreadPool(calls.size())) {
            List<Future<T>> futures = new ArrayList<>();
            for (Callable<T> call : calls) {
                futures.add(pool.submit(() -> {
                    ready.countDown();
                    go.await();
                    return call.call();
                }));
            }
            ready.await();
            go.countDown();
            List<T> results = new ArrayList<>();
            for (Future<T> future : futures) {
                results.add(future.get());
            }
            return results;
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException("interrupted while running calls in parallel", e);
        } catch (ExecutionException e) {
            throw new IllegalStateException("a parallel call failed", e.getCause());
        }
    }
}
