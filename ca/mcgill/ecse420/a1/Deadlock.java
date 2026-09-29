package ca.mcgill.ecse420.a1;

import java.util.concurrent.locks.Lock;
import java.util.concurrent.locks.ReentrantLock;

/**
 * Question 2: a program that demonstrates deadlock.
 * Thread 1 acquires lock A then lock B; thread 2 acquires lock B then lock A.
 * Each thread ends up holding one lock while waiting for the other, so the program hangs.
 * Run: java ca.mcgill.ecse420.a1.Deadlock   (stop it with Ctrl+C)
 */
public class Deadlock {

  private static final long SLP = 100;                 // SLP = sleep time in ms
  private static final Lock LA = new ReentrantLock();  // LA = lock A
  private static final Lock LB = new ReentrantLock();  // LB = lock B

  public static void main(String[] args) throws InterruptedException {
    Thread t1 = new Thread(() -> acq("T1", LA, "A", LB, "B")); // t1 = thread 1: A then B
    Thread t2 = new Thread(() -> acq("T2", LB, "B", LA, "A")); // t2 = thread 2: B then A

    t1.start();
    t2.start();
    t1.join();
    t2.join();
    System.out.println("Both threads finished."); // never printed
  }

  /**
   * acq = acquire both locks: takes l1, waits a little, takes l2, then releases both.
   * @param nm name of the thread, used in the messages
   * @param l1 first lock to acquire
   * @param n1 name of the first lock
   * @param l2 second lock to acquire
   * @param n2 name of the second lock
   */
  private static void acq(String nm, Lock l1, String n1, Lock l2, String n2) {
    l1.lock();
    System.out.println(nm + ": acquired lock " + n1);
    slp(SLP); // hold l1 long enough for the other thread to take its own first lock

    System.out.println(nm + ": trying to acquire lock " + n2 + " (while holding lock " + n1 + ")");
    l2.lock(); // blocks forever: the other thread holds l2 and is waiting for l1
    System.out.println(nm + ": acquired lock " + n2);

    l2.unlock();
    l1.unlock();
  }

  /** slp = sleep for ms milliseconds. */
  private static void slp(long ms) {
    try {
      Thread.sleep(ms);
    } catch (InterruptedException e) {
      Thread.currentThread().interrupt();
    }
  }
}