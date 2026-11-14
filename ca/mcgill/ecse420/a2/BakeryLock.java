package ca.mcgill.ecse420.a2;

import java.util.concurrent.atomic.AtomicIntegerArray;
import java.util.concurrent.atomic.AtomicLongArray;

/**
 * Lamport's Bakery lock from Chapter 2 of the course text (Question 1.5).
 *
 * <p>Each thread that wants the lock first goes through a <em>doorway</em>: it raises its flag and
 * takes a label one larger than every label it can see. It then waits while some other thread has
 * its flag up and a smaller (label, id) pair. Ties between equal labels are broken by thread id, so
 * the pairs are totally ordered and exactly one waiting thread has the smallest pair.
 *
 * <p>{@code flag} and {@code label} are atomic arrays because the elements of plain Java arrays are
 * not volatile: without that, the algorithm could fail for memory-model reasons rather than
 * algorithmic ones. Labels are {@code long}s, so they cannot overflow in any realistic run.
 */
public class BakeryLock implements Lock {

  /** Busy-wait iterations between two {@link Thread#yield()} calls while waiting. */
  private static final int SPINS_BEFORE_YIELD = 64;

  /** Number of threads the lock is built for. */
  private final int threadCount;

  /** {@code flag[t]} is 1 while thread t wants the lock or holds it, 0 otherwise. */
  private final AtomicIntegerArray flag;

  /** {@code label[t]} is thread t's ticket number; smaller numbers are served first. */
  private final AtomicLongArray label;

  /** Optional hook used only by the doorway experiment of Question 1.8 (null otherwise). */
  private final DoorwayObserver observer;

  /**
   * Receives an event when a thread starts and finishes the doorway. Used to check the
   * first-come-first-served property in Question 1.8; the plain lock passes {@code null}.
   */
  public interface DoorwayObserver {

    /**
     * Called at the start of {@code lock()}, before the thread reads any label.
     *
     * @param me id of the thread
     */
    void doorwayStarted(int me);

    /**
     * Called right after the thread has written its new label.
     *
     * @param me id of the thread
     */
    void doorwayFinished(int me);
  }

  /**
   * Creates a Bakery lock for n threads with ids {@code 0..n-1}.
   *
   * @param threadCount number of threads (n)
   */
  public BakeryLock(int threadCount) {
    this(threadCount, null);
  }

  /**
   * Creates a Bakery lock that reports doorway events to an observer (Question 1.8).
   *
   * @param threadCount number of threads (n)
   * @param observer receives doorway events, or null for none
   */
  public BakeryLock(int threadCount, DoorwayObserver observer) {
    this.threadCount = threadCount;
    this.flag = new AtomicIntegerArray(threadCount); // Nobody wants the lock at first.
    this.label = new AtomicLongArray(threadCount); // All labels start at 0.
    this.observer = observer;
  }

  @Override
  public void lock() {
    int me = ThreadID.get();
    if (observer != null) {
      observer.doorwayStarted(me);
    }

    // Doorway: announce interest and take a label larger than every label currently visible.
    flag.set(me, 1);
    label.set(me, maxLabel() + 1);
    if (observer != null) {
      observer.doorwayFinished(me);
    }

    // Waiting room: wait while some other interested thread has a smaller (label, id) pair.
    int spins = 0;
    while (someoneAhead(me)) {
      if (++spins % SPINS_BEFORE_YIELD == 0) {
        // With more threads than cores, the thread ahead of me may not be running.
        // Yielding gives it the core; it does not change the algorithm.
        Thread.yield();
      } else {
        Thread.onSpinWait(); // Hint to the CPU that this is a busy-wait loop.
      }
    }
  }

  @Override
  public void unlock() {
    flag.set(ThreadID.get(), 0); // No longer interested; the label is simply ignored from now on.
  }

  /**
   * Returns the largest label of any thread. Reads the labels one at a time, so two threads in
   * the doorway at the same time may compute the same maximum; the id tie-break handles that.
   *
   * @return the maximum label seen
   */
  private long maxLabel() {
    long max = 0;
    for (int t = 0; t < threadCount; t++) {
      max = Math.max(max, label.get(t));
    }
    return max;
  }

  /**
   * Returns true if some other thread has its flag up and a lexicographically smaller (label, id)
   * pair than the calling thread.
   *
   * @param me id of the calling thread
   * @return whether the caller must keep waiting
   */
  private boolean someoneAhead(int me) {
    long myLabel = label.get(me);
    for (int other = 0; other < threadCount; other++) {
      if (other == me || flag.get(other) == 0) {
        continue;
      }
      long otherLabel = label.get(other);
      if (otherLabel < myLabel || (otherLabel == myLabel && other < me)) {
        return true;
      }
    }
    return false;
  }
}
