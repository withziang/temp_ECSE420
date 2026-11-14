package ca.mcgill.ecse420.a2;

/**
 * Runs the Bakery lock experiments of Questions 1.6 and 1.8.
 *
 * <ol>
 *   <li>Question 1.6: the mutual exclusion test for n = 2 to 8.
 *   <li>Question 1.8: overtaking with n = 8, measured from the {@code lock()} call to entering the
 *       critical section, then measured from the end of the doorway, plus a strict
 *       first-come-first-served check.
 * </ol>
 *
 * <p>Usage: {@code java -cp out ca.mcgill.ecse420.a2.Question1Bakery [mutexIterations]
 * [overtakingIterations]}. Defaults: 100,000 and 20,000 critical-section entries per thread.
 */
public final class Question1Bakery {

  /** Number of threads for the overtaking experiments. */
  private static final int OVERTAKING_THREADS = 8;

  private Question1Bakery() {
    // Entry point only, not meant to be instantiated.
  }

  /**
   * Runs all Bakery lock experiments.
   *
   * @param args optional iteration counts for the mutual exclusion and overtaking tests
   */
  public static void main(String[] args) {
    int mutexIterations =
        args.length > 0 ? Integer.parseInt(args[0]) : MutualExclusionTest.DEFAULT_ITERATIONS;
    int overtakingIterations =
        args.length > 1 ? Integer.parseInt(args[1]) : OvertakingTest.DEFAULT_ITERATIONS;
    System.out.println("Available processors: " + Runtime.getRuntime().availableProcessors());

    // Question 1.6: mutual exclusion for n = 2..8.
    MutualExclusionTest.runRange("BakeryLock", BakeryLock::new, 2, 8, mutexIterations);

    // Question 1.8: overtaking from lock() to the critical section (plain lock, no hooks).
    OvertakingTest.runAndPrint(
        "BakeryLock", BakeryLock::new, OVERTAKING_THREADS, overtakingIterations);

    // Question 1.8: overtaking measured from the doorway, and the FCFS check (instrumented lock).
    BakeryDoorwayTest.runAndPrint(OVERTAKING_THREADS, overtakingIterations);
  }
}
