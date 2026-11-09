package ca.mcgill.ecse420.a2;

/**
 * Minimal lock interface shared by every lock in Assignment 2 (Filter, Bakery, and the baselines
 * used to check the test harness).
 *
 * <p>Implementations identify the calling thread with {@link ThreadID#get()}, which returns an id
 * in the range {@code 0..n-1}, as in Chapter 2 of the course text.
 */
public interface Lock {

  /** Blocks (spins) until the calling thread holds the lock. */
  void lock();

  /** Releases the lock held by the calling thread. */
  void unlock();
}
