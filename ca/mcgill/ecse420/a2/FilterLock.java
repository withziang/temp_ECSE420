package ca.mcgill.ecse420.a2;

import java.util.concurrent.atomic.AtomicIntegerArray;

/**
 * The Filter lock from Chapter 2 of the course text (Question 1.1).
 *
 * <p>The Filter lock generalizes Peterson's two-thread lock to n threads. It has n - 1 "waiting
 * rooms", called levels, that a thread must pass through before entering the critical section. At
 * each level L a thread announces that it is at level L, makes itself the victim of that level, and
 * waits while it is still the victim and some other thread is at level L or higher. At most n - L
 * threads can get past level L, so only one thread gets past level n - 1 into the critical section.
 *
 * <p>{@code level} and {@code victim} are {@link AtomicIntegerArray}s because the elements of a
 * plain {@code int[]} are not volatile in Java: without that, a thread could keep reading a stale
 * value from its cache or the compiler could reorder the writes, and the lock would break for
 * memory-model reasons rather than algorithmic ones.
 */
public class FilterLock implements Lock {

  /** Busy-wait iterations between two {@link Thread#yield()} calls while waiting at a level. */
  private static final int SPINS_BEFORE_YIELD = 64;

  /** Number of threads the lock is built for. */
  private final int threadCount;

  /** {@code level[t]} is the highest level thread t is trying to enter (0 = not trying). */
  private final AtomicIntegerArray level;

  /** {@code victim[L]} is the last thread to enter level L (index 0 is unused). */
  private final AtomicIntegerArray victim;

  /** Optional hook used only by the overtaking experiment of Question 1.4 (null otherwise). */
  private final LevelObserver observer;

  /**
   * Receives an event each time a thread enters or leaves a level. Used to measure overtaking at
   * every level in Question 1.4; the plain lock passes {@code null} and pays no cost.
   */
  public interface LevelObserver {

    /**
     * Called right after the thread has written {@code level[me] = L} and {@code victim[L] = me}.
     *
     * @param me id of the thread
     * @param currentLevel the level just entered
     */
    void enteredLevel(int me, int currentLevel);

    /**
     * Called right after the thread stops waiting at a level and moves up.
     *
     * @param me id of the thread
     * @param currentLevel the level just passed
     */
    void passedLevel(int me, int currentLevel);
  }

  /**
   * Creates a Filter lock for n threads with ids {@code 0..n-1}.
   *
   * @param threadCount number of threads (n)
   */
  public FilterLock(int threadCount) {
    this(threadCount, null);
  }

  /**
   * Creates a Filter lock that reports level events to an observer (Question 1.4).
   *
   * @param threadCount number of threads (n)
   * @param observer receives level events, or null for none
   */
  public FilterLock(int threadCount, LevelObserver observer) {
    this.threadCount = threadCount;
    this.level = new AtomicIntegerArray(threadCount); // All threads start at level 0.
    this.victim = new AtomicIntegerArray(threadCount);
    this.observer = observer;
  }

  @Override
  public void lock() {
    int me = ThreadID.get();
    for (int currentLevel = 1; currentLevel < threadCount; currentLevel++) {
      level.set(me, currentLevel); // Announce that I want to enter this level.
      victim.set(currentLevel, me); // Let the others go first at this level.
      if (observer != null) {
        observer.enteredLevel(me, currentLevel);
      }

      // Wait while I am the victim and some other thread is at this level or higher.
      int spins = 0;
      while (victim.get(currentLevel) == me && someoneElseAtOrAbove(me, currentLevel)) {
        if (++spins % SPINS_BEFORE_YIELD == 0) {
          // With more threads than cores, the thread that would let me go may not be running.
          // Yielding gives it the core; it does not change the algorithm.
          Thread.yield();
        } else {
          Thread.onSpinWait(); // Hint to the CPU that this is a busy-wait loop.
        }
      }
      if (observer != null) {
        observer.passedLevel(me, currentLevel);
      }
    }
  }

  @Override
  public void unlock() {
    int me = ThreadID.get();
    level.set(me, 0); // Leave every level at once.
  }

  /**
   * Returns true if some thread other than {@code me} is at {@code currentLevel} or above.
   *
   * @param me id of the calling thread
   * @param currentLevel the level being checked
   * @return whether another thread is at that level or higher
   */
  private boolean someoneElseAtOrAbove(int me, int currentLevel) {
    for (int other = 0; other < threadCount; other++) {
      if (other != me && level.get(other) >= currentLevel) {
        return true;
      }
    }
    return false;
  }
}
