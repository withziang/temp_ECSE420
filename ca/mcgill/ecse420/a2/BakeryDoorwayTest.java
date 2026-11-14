package ca.mcgill.ecse420.a2;

import java.util.Arrays;
import java.util.concurrent.atomic.AtomicLong;

/**
 * Measures overtaking in the Bakery lock relative to its doorway (Question 1.8).
 *
 * <p>Every acquisition gets three tickets: a <em>start</em> ticket when it enters the doorway,
 * an <em>end</em> ticket when it has written its label, and an <em>entry</em> ticket when it
 * enters the critical section. Start and end tickets come from the same atomic counter, so they
 * are totally ordered. Two numbers are reported:
 *
 * <ul>
 *   <li><b>Overtaken after the doorway</b>: B finished its doorway after A did, but entered the
 *       critical section before A. This can happen legally when the two doorways overlap (both read
 *       the same maximum, and the tie is broken by id).
 *   <li><b>First-come-first-served violations</b>: A finished its doorway <em>before B started
 *       its</em>, yet B entered before A. The Bakery lock guarantees this never happens, so the
 *       count must be 0.
 * </ul>
 */
public final class BakeryDoorwayTest {

  /** How long one run may take before it is reported as a timeout. */
  private static final long TIMEOUT_MILLIS = 300_000;

  private BakeryDoorwayTest() {
    // Utility class, not meant to be instantiated.
  }

  /** Records start and end tickets of the doorway for every thread and iteration. */
  private static final class Recorder implements BakeryLock.DoorwayObserver {
    /** {@code startTickets[t][i]}: when thread t started the doorway in its i-th acquisition. */
    final long[][] startTickets;

    /** {@code endTickets[t][i]}: when thread t finished the doorway in its i-th acquisition. */
    final long[][] endTickets;

    /** Shared by start and end events so that the two kinds of tickets are comparable. */
    final AtomicLong doorwayCounter = new AtomicLong();

    /** Current iteration of each thread; each entry is only touched by its own thread. */
    final int[] currentIteration;

    Recorder(int threadCount, int iterations) {
      startTickets = new long[threadCount][iterations];
      endTickets = new long[threadCount][iterations];
      currentIteration = new int[threadCount];
    }

    @Override
    public void doorwayStarted(int me) {
      startTickets[me][currentIteration[me]] = doorwayCounter.getAndIncrement();
    }

    @Override
    public void doorwayFinished(int me) {
      endTickets[me][currentIteration[me]] = doorwayCounter.getAndIncrement();
    }
  }

  /**
   * Counts, for each acquisition A, the acquisitions B with {@code start[B] > end[A]} and {@code
   * entry[B] < entry[A]}, i.e. B arrived strictly after A's doorway but still got in first.
   *
   * <p>Acquisitions are visited in decreasing end-ticket order; every acquisition whose start
   * ticket is above the current end ticket is inserted into a Fenwick tree over entry tickets,
   * which then answers "how many of them entered before A". O(N log N) overall.
   *
   * @param startTickets doorway start tickets, indexed [thread][iteration]
   * @param endTickets doorway end tickets, indexed [thread][iteration]
   * @param entryTickets critical-section entry tickets, exactly {@code 0..N-1}
   * @return total number of first-come-first-served violations
   */
  static long countFcfsViolations(
      long[][] startTickets, long[][] endTickets, long[][] entryTickets) {
    int iterations = startTickets[0].length;
    int total = startTickets.length * iterations;

    Integer[] byStart = new Integer[total];
    Integer[] byEnd = new Integer[total];
    for (int k = 0; k < total; k++) {
      byStart[k] = k;
      byEnd[k] = k;
    }
    // Both in decreasing order.
    Arrays.sort(
        byStart,
        (a, b) ->
            Long.compare(
                startTickets[b / iterations][b % iterations],
                startTickets[a / iterations][a % iterations]));
    Arrays.sort(
        byEnd,
        (a, b) ->
            Long.compare(
                endTickets[b / iterations][b % iterations],
                endTickets[a / iterations][a % iterations]));

    long[] fenwick = new long[total + 1];
    long violations = 0;
    int next = 0;
    for (int k = 0; k < total; k++) {
      int a = byEnd[k];
      long endA = endTickets[a / iterations][a % iterations];

      // Insert every acquisition that started its doorway after A finished its own.
      while (next < total
          && startTickets[byStart[next] / iterations][byStart[next] % iterations] > endA) {
        int b = byStart[next++];
        int entryB = (int) entryTickets[b / iterations][b % iterations];
        for (int pos = entryB + 1; pos <= total; pos += pos & -pos) {
          fenwick[pos]++;
        }
      }

      // Of those, count the ones that entered the critical section before A.
      int entryA = (int) entryTickets[a / iterations][a % iterations];
      for (int pos = entryA; pos > 0; pos -= pos & -pos) {
        violations += fenwick[pos];
      }
    }
    return violations;
  }

  /**
   * Runs the instrumented Bakery lock with n threads and prints both measurements.
   *
   * @param threadCount number of threads (8 for the assignment)
   * @param iterations critical-section entries per thread
   * @return true if the run finished and there were no first-come-first-served violations
   */
  public static boolean runAndPrint(int threadCount, int iterations) {
    Recorder recorder = new Recorder(threadCount, iterations);
    BakeryLock lock = new BakeryLock(threadCount, recorder);
    long[][] entryTickets = new long[threadCount][iterations];
    AtomicLong entryCounter = new AtomicLong();

    boolean finished =
        Workers.runAll(
            threadCount,
            id -> {
              for (int i = 0; i < iterations; i++) {
                recorder.currentIteration[id] = i;
                lock.lock();
                try {
                  entryTickets[id][i] = entryCounter.getAndIncrement();
                } finally {
                  lock.unlock();
                }
              }
            },
            TIMEOUT_MILLIS);

    System.out.printf(
        "%nOvertaking after the doorway: BakeryLock, n = %d (%,d iterations per thread)%n",
        threadCount, iterations);
    if (!finished) {
      System.out.println("TIMEOUT: the run did not finish.");
      return false;
    }

    long[][] overtakes = new long[threadCount][iterations];
    OvertakingTest.countOvertakes(recorder.endTickets, entryTickets, overtakes);
    long total = (long) threadCount * iterations;
    long overtaken = 0;
    long max = 0;
    for (long[] perThread : overtakes) {
      overtaken += Arrays.stream(perThread).filter(v -> v > 0).count();
      max = Math.max(max, Arrays.stream(perThread).max().orElse(0));
    }
    System.out.printf(
        "Measured from end of doorway: %.2f%% of acquisitions overtaken, max %,d overtakes%n",
        100.0 * overtaken / total, max);

    long violations =
        countFcfsViolations(recorder.startTickets, recorder.endTickets, entryTickets);
    System.out.printf(
        "First-come-first-served violations (B started doorway after A finished it, "
            + "yet entered first): %,d -> %s%n",
        violations, violations == 0 ? "PASS" : "FAIL");
    return violations == 0;
  }
}
