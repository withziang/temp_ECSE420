package ca.mcgill.ecse420.a2;

import java.util.concurrent.CountDownLatch;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.IntConsumer;

/**
 * Starts n worker threads with ids {@code 0..n-1}, releases them at the same moment, and waits for
 * them to finish. Shared by both test harnesses.
 */
final class Workers {

  private Workers() {
    // Utility class, not meant to be instantiated.
  }

  /**
   * Runs {@code body} on n threads at once.
   *
   * @param threadCount number of worker threads (n)
   * @param body code each worker runs; receives the worker's id
   * @param timeoutMillis how long to wait before giving up (a broken lock may deadlock)
   * @return true if every worker finished in time, false on timeout
   * @throws RuntimeException if any worker threw an exception
   */
  static boolean runAll(int threadCount, IntConsumer body, long timeoutMillis) {
    CountDownLatch startGate = new CountDownLatch(1);
    AtomicReference<Throwable> failure = new AtomicReference<>();
    Thread[] threads = new Thread[threadCount];

    for (int i = 0; i < threadCount; i++) {
      final int id = i;
      threads[i] =
          new Thread(
              () -> {
                ThreadID.set(id);
                try {
                  startGate.await(); // Start all workers together to maximize contention.
                  body.accept(id);
                } catch (Throwable t) {
                  failure.compareAndSet(null, t);
                }
              },
              "worker-" + id);
      threads[i].setDaemon(true); // A deadlocked worker must not keep the JVM alive.
      threads[i].start();
    }

    startGate.countDown();
    long deadline = System.currentTimeMillis() + timeoutMillis;
    try {
      for (Thread thread : threads) {
        long remaining = deadline - System.currentTimeMillis();
        if (remaining > 0) {
          thread.join(remaining);
        }
      }
    } catch (InterruptedException e) {
      Thread.currentThread().interrupt();
      return false;
    }

    if (failure.get() != null) {
      throw new RuntimeException("A worker thread failed", failure.get());
    }
    for (Thread thread : threads) {
      if (thread.isAlive()) {
        return false;
      }
    }
    return true;
  }
}
