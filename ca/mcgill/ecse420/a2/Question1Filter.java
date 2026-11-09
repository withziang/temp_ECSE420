package ca.mcgill.ecse420.a2;

/**
 * Runs the Filter lock experiments of Questions 1.2 and 1.4.
 *
 * <ol>
 *   <li>Question 1.2: the mutual exclusion test for n = 2 to 8.
 *   <li>Question 1.4: overtaking with n = 8, measured from the {@code lock()} call to entering the
 *       critical section, then at every level of the lock.
 * </ol>
 *
 * <p>Usage: {@code java ca.mcgill.ecse420.a2.Question1Filter [mutexIterations]
 * [overtakingIterations]}. Defaults: 100,000 and 20,000 critical-section entries per thread.
 */
public final class Question1Filter {

  /** Number of threads for the overtaking experiments. */
  private static final int OVERTAKING_THREADS = 8;

  private Question1Filter() {
    // Entry point only, not meant to be instantiated.
  }

  /**
   * Runs all Filter lock experiments.
   *
   * @param args optional iteration counts for the mutual exclusion and overtaking tests
   */
  public static void main(String[] args) {
    int mutexIterations =
        args.length > 0 ? Integer.parseInt(args[0]) : MutualExclusionTest.DEFAULT_ITERATIONS;
    int overtakingIterations =
        args.length > 1 ? Integer.parseInt(args[1]) : OvertakingTest.DEFAULT_ITERATIONS;
    System.out.println("Available processors: " + Runtime.getRuntime().availableProcessors());

    // Question 1.2: mutual exclusion for n = 2..8.
    MutualExclusionTest.runRange("FilterLock", FilterLock::new, 2, 8, mutexIterations);

    // Question 1.4: overtaking from lock() to the critical section (plain lock, no hooks).
    OvertakingTest.runAndPrint(
        "FilterLock", FilterLock::new, OVERTAKING_THREADS, overtakingIterations);

    // Question 1.4: overtaking at each level (instrumented lock).
    FilterLevelOvertakingTest.runAndPrint(OVERTAKING_THREADS, overtakingIterations);
  }
}
