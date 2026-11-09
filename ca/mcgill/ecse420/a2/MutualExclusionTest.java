package ca.mcgill.ecse420.a2;

import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;
import java.util.function.IntFunction;

/**
 * Test harness for n-thread mutual exclusion (Questions 1.2 and 1.6).
 *
 * <p>Each of the n threads repeatedly acquires the lock and, inside the critical section:
 *
 * <ol>
 *   <li>increments an <em>occupancy</em> counter and checks that it is exactly 1 (it would be 2 or
 *       more if two threads were inside at once), then
 *   <li>increments a plain, non-atomic, non-volatile shared counter.
 * </ol>
 *
 * <p>The lock passes when no overlap is observed and the final counter equals {@code n *
 * iterations}. A lost update on the plain counter means two threads executed {@code counter++}
 * concurrently, which also shows a violation. The two checks are complementary: the occupancy check
 * catches overlaps directly, and the counter check catches them even when the overlap was too short
 * to be seen.
 */
public final class MutualExclusionTest {

  /** Default number of critical-section entries per thread. */
  public static final int DEFAULT_ITERATIONS = 100_000;

  /** How long one run may take before it is reported as a timeout (possible deadlock). */
  private static final long TIMEOUT_MILLIS = 120_000;

  private MutualExclusionTest() {
    // Utility class, not meant to be instantiated.
  }

  /** State shared by all workers during one run. */
  private static final class SharedState {
    /** Deliberately plain (not volatile, not atomic): only the lock protects it. */
    int counter;

    /** Number of threads currently inside the critical section. */
    final AtomicInteger occupancy = new AtomicInteger();

    /** Number of times a thread found someone else already inside the critical section. */
    final AtomicLong overlaps = new AtomicLong();
  }

  /**
   * Result of one run.
   *
   * @param lockName name of the lock under test
   * @param threadCount number of threads (n)
   * @param iterations critical-section entries per thread
   * @param expected expected final counter value ({@code n * iterations})
   * @param actual observed final counter value
   * @param overlaps number of times two threads were seen in the critical section together
   * @param timedOut true if the run did not finish (possible deadlock)
   * @param elapsedMillis wall-clock time of the run
   */
  public record Result(
      String lockName,
      int threadCount,
      int iterations,
      long expected,
      long actual,
      long overlaps,
      boolean timedOut,
      long elapsedMillis) {

    /** Returns true if the run finished with no mutual exclusion violation. */
    public boolean passed() {
      return !timedOut && expected == actual && overlaps == 0;
    }
  }

  /**
   * Runs the test once with n threads.
   *
   * @param lockName name printed in the results
   * @param lockFactory creates a fresh lock for n threads (for example {@code FilterLock::new})
   * @param threadCount number of threads (n)
   * @param iterations critical-section entries per thread
   * @return the result of the run
   */
  public static Result run(
      String lockName, IntFunction<Lock> lockFactory, int threadCount, int iterations) {
    Lock lock = lockFactory.apply(threadCount);
    SharedState state = new SharedState();

    long start = System.nanoTime();
    boolean finished =
        Workers.runAll(
            threadCount,
            id -> {
              for (int i = 0; i < iterations; i++) {
                lock.lock();
                try {
                  if (state.occupancy.incrementAndGet() != 1) {
                    state.overlaps.incrementAndGet(); // Someone else is inside: violation.
                  }
                  state.counter++; // Non-atomic read-modify-write protected only by the lock.
                  state.occupancy.decrementAndGet();
                } finally {
                  lock.unlock();
                }
              }
            },
            TIMEOUT_MILLIS);
    long elapsedMillis = (System.nanoTime() - start) / 1_000_000;

    // Thread.join() gives a happens-before edge, so the plain counter is safe to read here.
    return new Result(
        lockName,
        threadCount,
        iterations,
        (long) threadCount * iterations,
        state.counter,
        state.overlaps.get(),
        !finished,
        elapsedMillis);
  }

  /**
   * Runs the test for every n from {@code minThreads} to {@code maxThreads} and prints a table.
   *
   * @param lockName name printed in the results
   * @param lockFactory creates a fresh lock for n threads
   * @param minThreads smallest n (2 for the assignment)
   * @param maxThreads largest n (8 for the assignment)
   * @param iterations critical-section entries per thread
   * @return true if every run passed
   */
  public static boolean runRange(
      String lockName,
      IntFunction<Lock> lockFactory,
      int minThreads,
      int maxThreads,
      int iterations) {
    System.out.printf(
        "%nMutual exclusion test: %s (%,d iterations per thread)%n", lockName, iterations);
    System.out.printf(
        "%3s %14s %14s %10s %10s %8s%n",
        "n", "expected", "actual", "overlaps", "time(ms)", "result");

    boolean allPassed = true;
    for (int n = minThreads; n <= maxThreads; n++) {
      Result result = run(lockName, lockFactory, n, iterations);
      String verdict = result.timedOut() ? "TIMEOUT" : (result.passed() ? "PASS" : "FAIL");
      System.out.printf(
          "%3d %,14d %,14d %,10d %10d %8s%n",
          n,
          result.expected(),
          result.actual(),
          result.overlaps(),
          result.elapsedMillis(),
          verdict);
      allPassed &= result.passed();
    }
    System.out.printf("Overall: %s%n", allPassed ? "PASS" : "FAIL");
    return allPassed;
  }
}
