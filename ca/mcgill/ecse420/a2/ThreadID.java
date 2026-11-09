package ca.mcgill.ecse420.a2;

/**
 * Gives each worker thread a small integer id in the range {@code 0..n-1}.
 *
 * <p>The course text uses a self-incrementing counter for this. Here the test harness assigns the
 * ids explicitly with {@link #set(int)} when each worker starts, so ids are always {@code 0..n-1}
 * for every run, even when the same JVM runs tests for n = 2, 3, ..., 8 one after another.
 */
public final class ThreadID {

  /** Id of the current thread, or -1 if the harness has not assigned one. */
  private static final ThreadLocal<Integer> threadId = ThreadLocal.withInitial(() -> -1);

  private ThreadID() {
    // Utility class, not meant to be instantiated.
  }

  /**
   * Assigns an id to the calling thread. Called once by the harness at the start of each worker.
   *
   * @param id the id, in the range {@code 0..n-1}
   */
  public static void set(int id) {
    threadId.set(id);
  }

  /**
   * Returns the id of the calling thread.
   *
   * @return the id assigned with {@link #set(int)}
   * @throws IllegalStateException if no id was assigned to this thread
   */
  public static int get() {
    int id = threadId.get();
    if (id < 0) {
      throw new IllegalStateException(
          "ThreadID.get() called from a thread with no id. Call ThreadID.set(i) first.");
    }
    return id;
  }
}
