package ca.mcgill.ecse420.a1;

import java.lang.management.ManagementFactory;
import java.lang.management.ThreadInfo;
import java.lang.management.ThreadMXBean;
import java.util.concurrent.locks.Lock;
import java.util.concurrent.locks.ReentrantLock;

/**
 * Question 2: a program that demonstrates deadlock.
 * Thread 1 acquires lock A then lock B; thread 2 acquires lock B then lock A.
 * Each thread ends up holding one lock while waiting for the other, so the program hangs.
 * Run: java ca.mcgill.ecse420.a1.Deadlock   (stop it with Ctrl+C)
 */
public class Deadlock {
 
  // How long each thread holds its first lock before requesting the second one (ms)
  private static final long HOLD_TIME_MS = 100;
  // How long main waits for the threads before checking for deadlock (ms)
  private static final long JOIN_TIMEOUT_MS = 2000;
 
  private static final Lock lockA = new ReentrantLock();
  private static final Lock lockB = new ReentrantLock();
 
  public static void main(String[] args) throws InterruptedException {
    boolean useFix = args.length > 0 && args[0].equals("fixed");
 
    Thread thread1 = new Thread(() -> acquireBothLocks("Thread 1", lockA, "A", lockB, "B"),
        "Thread 1");
    Thread thread2;
    if (useFix) {
      // Question 2.2: same global order as Thread 1 (A before B), so no circular wait
      thread2 = new Thread(() -> acquireBothLocks("Thread 2", lockA, "A", lockB, "B"),
          "Thread 2");
    } else {
      // Question 2.1: opposite order (B before A), which allows a circular wait
      thread2 = new Thread(() -> acquireBothLocks("Thread 2", lockB, "B", lockA, "A"),
          "Thread 2");
    }
    // Daemon threads do not keep the JVM alive, so the program can exit after a deadlock
    thread1.setDaemon(true);
    thread2.setDaemon(true);
 
    thread1.start();
    thread2.start();
    thread1.join(JOIN_TIMEOUT_MS);
    thread2.join(JOIN_TIMEOUT_MS);
 
    if (!thread1.isAlive() && !thread2.isAlive()) {
      System.out.println("Both threads finished.");
    } else {
      reportDeadlock();
    }
  }
 
  /**
   * Acquires the first lock, waits a little, acquires the second lock, then releases both.
   *
   * @param threadName name of the thread, used in the messages
   * @param firstLock first lock to acquire
   * @param firstName name of the first lock
   * @param secondLock second lock to acquire
   * @param secondName name of the second lock
   */
  private static void acquireBothLocks(String threadName, Lock firstLock, String firstName,
      Lock secondLock, String secondName) {
    firstLock.lock();
    try {
      System.out.println(threadName + ": acquired lock " + firstName);
      sleep(HOLD_TIME_MS); // gives the other thread time to take its own first lock
 
      System.out.println(threadName + ": waiting for lock " + secondName
          + " while holding lock " + firstName);
      secondLock.lock(); // in the deadlock version this blocks forever
      try {
        System.out.println(threadName + ": acquired lock " + secondName);
      } finally {
        secondLock.unlock();
      }
    } finally {
      firstLock.unlock();
    }
  }
 
  /** Uses the JVM's built-in deadlock detector to print which threads are deadlocked. */
  private static void reportDeadlock() {
    ThreadMXBean threadBean = ManagementFactory.getThreadMXBean();
    long[] deadlockedIds = threadBean.findDeadlockedThreads();
    if (deadlockedIds == null) {
      System.out.println("Threads still running after " + JOIN_TIMEOUT_MS
          + " ms, but no deadlock was detected.");
      return;
    }
    System.out.println("DEADLOCK detected after " + JOIN_TIMEOUT_MS + " ms:");
    for (ThreadInfo info : threadBean.getThreadInfo(deadlockedIds)) {
      System.out.println("  " + info.getThreadName() + " is blocked on a lock owned by "
          + info.getLockOwnerName());
    }
    System.out.println("\"Both threads finished.\" was never printed.");
  }
 
  /**
   * Sleeps for the given number of milliseconds.
   *
   * @param milliseconds time to sleep
   */
  private static void sleep(long milliseconds) {
    try {
      Thread.sleep(milliseconds);
    } catch (InterruptedException e) {
      Thread.currentThread().interrupt();
    }
  }
}