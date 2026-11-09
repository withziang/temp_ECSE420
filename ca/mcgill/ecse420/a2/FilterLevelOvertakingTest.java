package ca.mcgill.ecse420.a2;

import java.util.Arrays;
import java.util.concurrent.atomic.AtomicLong;

/**
 * Measures overtaking at every level of the Filter lock (Question 1.4).
 *
 * <p>For each level L, each acquisition gets an <em>enter ticket</em> when the thread has written
 * {@code level[me] = L} and {@code victim[L] = me}, and a <em>pass ticket</em> when it stops
 * waiting and moves up. At level L, acquisition A is overtaken by acquisition B when B entered L
 * after A but passed L before A. The counting is the same as in {@link OvertakingTest}, done once
 * per level.
 *
 * <p>The tickets come from atomic counters, so recording them adds a little synchronization to the
 * lock. This can change the timing slightly; the overall measurement in {@link OvertakingTest} uses
 * the plain lock without this instrumentation.
 */
public final class FilterLevelOvertakingTest {

  /** How long one run may take before it is reported as a timeout. */
  private static final long TIMEOUT_MILLIS = 300_000;

  private FilterLevelOvertakingTest() {
    // Utility class, not meant to be instantiated.
  }

  /** Records enter and pass tickets for every thread, level and iteration. */
  private static final class Recorder implements FilterLock.LevelObserver {
    /** {@code enterTickets[L][t][i]}: when thread t entered level L in its i-th acquisition. */
    final long[][][] enterTickets;

    /** {@code passTickets[L][t][i]}: when thread t passed level L in its i-th acquisition. */
    final long[][][] passTickets;

    /** One enter counter and one pass counter per level, so pass tickets are 0..N-1. */
    final AtomicLong[] enterCounters;

    final AtomicLong[] passCounters;

    /** Current iteration of each thread; each entry is only touched by its own thread. */
    final int[] currentIteration;

    Recorder(int threadCount, int iterations) {
      enterTickets = new long[threadCount][threadCount][iterations];
      passTickets = new long[threadCount][threadCount][iterations];
      enterCounters = new AtomicLong[threadCount];
      passCounters = new AtomicLong[threadCount];
      for (int level = 0; level < threadCount; level++) {
        enterCounters[level] = new AtomicLong();
        passCounters[level] = new AtomicLong();
      }
      currentIteration = new int[threadCount];
    }

    @Override
    public void enteredLevel(int me, int currentLevel) {
      enterTickets[currentLevel][me][currentIteration[me]] =
          enterCounters[currentLevel].getAndIncrement();
    }

    @Override
    public void passedLevel(int me, int currentLevel) {
      passTickets[currentLevel][me][currentIteration[me]] =
          passCounters[currentLevel].getAndIncrement();
    }
  }

  /**
   * Runs the Filter lock with n threads and prints, for every level, how often and how many times
   * acquisitions were overtaken at that level.
   *
   * @param threadCount number of threads (8 for the assignment)
   * @param iterations critical-section entries per thread
   * @return true if the run finished in time
   */
  public static boolean runAndPrint(int threadCount, int iterations) {
    Recorder recorder = new Recorder(threadCount, iterations);
    FilterLock lock = new FilterLock(threadCount, recorder);

    boolean finished =
        Workers.runAll(
            threadCount,
            id -> {
              for (int i = 0; i < iterations; i++) {
                recorder.currentIteration[id] = i;
                lock.lock();
                lock.unlock();
              }
            },
            TIMEOUT_MILLIS);

    System.out.printf(
        "%nOvertaking per level: FilterLock, n = %d (%,d iterations per thread)%n",
        threadCount, iterations);
    if (!finished) {
      System.out.println("TIMEOUT: the run did not finish.");
      return false;
    }

    System.out.printf("%5s %12s %10s %10s%n", "level", "% overtaken", "mean", "max");
    for (int level = 1; level < threadCount; level++) {
      long[][] overtakes = new long[threadCount][iterations];
      OvertakingTest.countOvertakes(
          recorder.enterTickets[level], recorder.passTickets[level], overtakes);

      long total = (long) threadCount * iterations;
      long overtaken = 0;
      long sum = 0;
      long max = 0;
      for (long[] perThread : overtakes) {
        overtaken += Arrays.stream(perThread).filter(v -> v > 0).count();
        sum += Arrays.stream(perThread).sum();
        max = Math.max(max, Arrays.stream(perThread).max().orElse(0));
      }
      System.out.printf(
          "%5d %11.2f%% %10.2f %,10d%n",
          level, 100.0 * overtaken / total, (double) sum / total, max);
    }
    return true;
  }
}
