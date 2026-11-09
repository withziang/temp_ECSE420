package ca.mcgill.ecse420.a2;

import java.util.concurrent.locks.ReentrantLock;

/**
 * Checks that the test harness itself works, before it is used on the Filter and Bakery locks.
 *
 * <ul>
 *   <li>{@link NoLock} does nothing, so the mutual exclusion test <em>must</em> report FAIL. This
 *       shows the test can detect violations.
 *   <li>{@link JavaLock} wraps Java's {@link ReentrantLock}, a known-correct lock, so the test
 *       <em>must</em> report PASS.
 *   <li>The overtaking test is run on Java's unfair and fair locks. The fair lock serves threads
 *       roughly in arrival order, so it should show much less overtaking than the unfair one.
 * </ul>
 *
 * <p>Run with: {@code java -cp out ca.mcgill.ecse420.a2.HarnessSelfCheck}
 */
public final class HarnessSelfCheck {

  private HarnessSelfCheck() {
    // Entry point only, not meant to be instantiated.
  }

  /** A "lock" that provides no mutual exclusion at all. */
  static final class NoLock implements Lock {
    @Override
    public void lock() {
      // Intentionally empty.
    }

    @Override
    public void unlock() {
      // Intentionally empty.
    }
  }

  /** Adapter from Java's {@link ReentrantLock} to the assignment's {@link Lock} interface. */
  static final class JavaLock implements Lock {
    private final ReentrantLock delegate;

    JavaLock(boolean fair) {
      delegate = new ReentrantLock(fair);
    }

    @Override
    public void lock() {
      delegate.lock();
    }

    @Override
    public void unlock() {
      delegate.unlock();
    }
  }

  /**
   * Runs the self-check.
   *
   * @param args unused
   */
  public static void main(String[] args) {
    int iterations = 100_000;

    boolean noLockPassed =
        MutualExclusionTest.runRange("NoLock", n -> new NoLock(), 2, 8, iterations);
    boolean javaLockPassed =
        MutualExclusionTest.runRange("ReentrantLock", n -> new JavaLock(false), 2, 8, iterations);

    OvertakingTest.runAndPrint(
        "ReentrantLock (unfair)", n -> new JavaLock(false), 8, OvertakingTest.DEFAULT_ITERATIONS);
    OvertakingTest.runAndPrint(
        "ReentrantLock (fair)", n -> new JavaLock(true), 8, OvertakingTest.DEFAULT_ITERATIONS);

    // The harness is trustworthy if it catches the broken lock and accepts the correct one.
    boolean harnessOk = !noLockPassed && javaLockPassed;
    System.out.printf(
        "%nHarness self-check: %s%n",
        harnessOk ? "OK (NoLock failed as expected, ReentrantLock passed)" : "PROBLEM");
  }
}
