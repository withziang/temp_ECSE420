package ca.mcgill.ecse420.a2;

import java.util.Arrays;
import java.util.concurrent.atomic.AtomicLong;
import java.util.function.IntFunction;

/**
 * Measures how often threads are "overtaken" (Questions 1.4 and 1.8).
 *
 * <p>Every acquisition gets two numbers:
 *
 * <ul>
 *   <li>a <em>request ticket</em>, taken when the thread starts competing for the lock (just before
 *       {@code lock()}, or at {@link Doorway#mark()} if the lock calls it), and
 *   <li>an <em>entry ticket</em>, taken when the thread enters the critical section.
 * </ul>
 *
 * <p>Acquisition A is <em>overtaken</em> by acquisition B when B requested the lock after A but
 * entered the critical section before A. The number of times A is overtaken is the number of such
 * B. Because the same harness is used for both locks, the Filter and Bakery numbers can be compared
 * directly.
 */
public final class OvertakingTest {

  /** Default number of critical-section entries per thread. */
  public static final int DEFAULT_ITERATIONS = 20_000;

  /** How long one run may take before it is reported as a timeout (possible deadlock). */
  private static final long TIMEOUT_MILLIS = 120_000;

  private OvertakingTest() {
    // Utility class, not meant to be instantiated.
  }

  /**
   * Result of one run. Arrays are indexed by thread id.
   *
   * @param lockName name of the lock under test
   * @param threadCount number of threads (n)
   * @param iterations critical-section entries per thread
   * @param overtakes {@code overtakes[t][i]} is how many times the i-th acquisition of thread t was
   *     overtaken
   * @param timedOut true if the run did not finish (possible deadlock)
   */
  public record Result(
      String lockName, int threadCount, int iterations, long[][] overtakes, boolean timedOut) {

    /** Largest number of times any single acquisition was overtaken. */
    public long maxOvertakes() {
      long max = 0;
      for (long[] perThread : overtakes) {
        for (long value : perThread) {
          max = Math.max(max, value);
        }
      }
      return max;
    }

    /** Fraction of acquisitions that were overtaken at least once. */
    public double fractionOvertaken() {
      long overtaken = 0;
      long total = 0;
      for (long[] perThread : overtakes) {
        for (long value : perThread) {
          total++;
          if (value > 0) {
            overtaken++;
          }
        }
      }
      return total == 0 ? 0 : (double) overtaken / total;
    }
  }

  /**
   * Runs the overtaking measurement once with n threads.
   *
   * @param lockName name printed in the results
   * @param lockFactory creates a fresh lock for n threads
   * @param threadCount number of threads (n)
   * @param iterations critical-section entries per thread
   * @return the result of the run
   */
  public static Result run(
      String lockName, IntFunction<Lock> lockFactory, int threadCount, int iterations) {
    Lock lock = lockFactory.apply(threadCount);
    long[][] requestTickets = new long[threadCount][iterations];
    long[][] entryTickets = new long[threadCount][iterations];
    AtomicLong entryCounter = new AtomicLong();
    Doorway.reset();

    boolean finished =
        Workers.runAll(
            threadCount,
            id -> {
              for (int i = 0; i < iterations; i++) {
                Doorway.begin(); // Request ticket (the lock may refresh it with Doorway.mark()).
                lock.lock();
                try {
                  requestTickets[id][i] = Doorway.end();
                  entryTickets[id][i] = entryCounter.getAndIncrement();
                } finally {
                  lock.unlock();
                }
              }
            },
            TIMEOUT_MILLIS);

    long[][] overtakes = new long[threadCount][iterations];
    if (finished) {
      countOvertakes(requestTickets, entryTickets, overtakes);
    }
    return new Result(lockName, threadCount, iterations, overtakes, !finished);
  }

  /**
   * For each acquisition, counts the acquisitions that requested later but entered earlier.
   *
   * <p>This is an inversion count. Acquisitions are visited in decreasing request order, and a
   * Fenwick (binary indexed) tree over entry tickets counts how many of the later requests have
   * already entered. It runs in O(N log N) for N acquisitions.
   */
  private static void countOvertakes(
      long[][] requestTickets, long[][] entryTickets, long[][] overtakes) {
    int threadCount = requestTickets.length;
    int iterations = requestTickets[0].length;
    int total = threadCount * iterations;

    // Flatten (thread, index) pairs and sort them by request ticket.
    Integer[] order = new Integer[total];
    for (int k = 0; k < total; k++) {
      order[k] = k;
    }
    Arrays.sort(
        order,
        (a, b) ->
            Long.compare(
                requestTickets[a / iterations][a % iterations],
                requestTickets[b / iterations][b % iterations]));

    // Entry tickets are exactly 0..total-1, so they index the Fenwick tree directly.
    long[] fenwick = new long[total + 1];
    for (int k = total - 1; k >= 0; k--) {
      int thread = order[k] / iterations;
      int index = order[k] % iterations;
      int entry = (int) entryTickets[thread][index];

      // Later requests that entered before this one = overtakes.
      long count = 0;
      for (int pos = entry; pos > 0; pos -= pos & -pos) {
        count += fenwick[pos];
      }
      overtakes[thread][index] = count;

      for (int pos = entry + 1; pos <= total; pos += pos & -pos) {
        fenwick[pos]++;
      }
    }
  }

  /**
   * Runs the measurement and prints a summary per thread and overall.
   *
   * @param lockName name printed in the results
   * @param lockFactory creates a fresh lock for n threads
   * @param threadCount number of threads (8 for the assignment)
   * @param iterations critical-section entries per thread
   * @return the result, for further analysis
   */
  public static Result runAndPrint(
      String lockName, IntFunction<Lock> lockFactory, int threadCount, int iterations) {
    Result result = run(lockName, lockFactory, threadCount, iterations);
    System.out.printf(
        "%nOvertaking test: %s, n = %d (%,d iterations per thread)%n",
        lockName, threadCount, iterations);
    if (result.timedOut()) {
      System.out.println("TIMEOUT: the run did not finish (possible deadlock).");
      return result;
    }

    System.out.printf("%6s %12s %12s %12s%n", "thread", "% overtaken", "mean", "max");
    for (int t = 0; t < threadCount; t++) {
      long[] perThread = result.overtakes()[t];
      long overtaken = Arrays.stream(perThread).filter(v -> v > 0).count();
      double mean = Arrays.stream(perThread).average().orElse(0);
      long max = Arrays.stream(perThread).max().orElse(0);
      System.out.printf(
          "%6d %11.2f%% %12.2f %,12d%n", t, 100.0 * overtaken / perThread.length, mean, max);
    }
    System.out.printf(
        "All threads: %.2f%% of acquisitions overtaken, max %,d overtakes%n",
        100.0 * result.fractionOvertaken(), result.maxOvertakes());
    return result;
  }
}
