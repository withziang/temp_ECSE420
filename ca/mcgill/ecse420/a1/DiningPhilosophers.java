package ca.mcgill.ecse420.a1;

import java.lang.management.ManagementFactory;
import java.lang.management.ThreadMXBean;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.locks.ReentrantLock;


public class DiningPhilosophers {

  private static final int MAX_ACTION_TIME_MS = 10; // longest time spent thinking or eating
  private static final long DEADLOCK_CHECK_INTERVAL_MS = 100;

  public static void main(String[] args) throws InterruptedException {
    String mode = args.length > 0 ? args[0] : "nostarvation";
    int durationSeconds = args.length > 1 ? Integer.parseInt(args[1]) : 8;
    int numberOfPhilosophers = args.length > 2 ? Integer.parseInt(args[2]) : 5;

    boolean orderedChopsticks;
    boolean fairLocks;
    switch (mode) {
      case "deadlock":
        orderedChopsticks = false;
        fairLocks = false;
        break;
      case "nodeadlock":
        orderedChopsticks = true;
        fairLocks = false;
        break;
      case "nostarvation":
        orderedChopsticks = true;
        fairLocks = true;
        break;
      default:
        System.out.println("Unknown mode '" + mode
            + "'. Use deadlock, nodeadlock or nostarvation.");
        return;
    }
    System.out.println("Mode: " + mode + ", " + numberOfPhilosophers + " philosophers, "
        + durationSeconds + " s, ordered chopsticks: " + orderedChopsticks + ", fair locks: "
        + fairLocks);

    // Fair locks hand the lock to the longest-waiting thread; non-fair locks allow barging
    ReentrantLock[] chopsticks = new ReentrantLock[numberOfPhilosophers];
    for (int i = 0; i < numberOfPhilosophers; i++) {
      chopsticks[i] = new ReentrantLock(fairLocks);
    }

    Philosopher[] philosophers = new Philosopher[numberOfPhilosophers];
    ExecutorService executor = Executors.newFixedThreadPool(numberOfPhilosophers);
    for (int i = 0; i < numberOfPhilosophers; i++) {
      int left = i;
      int right = (i + 1) % numberOfPhilosophers;
      int first = orderedChopsticks ? Math.min(left, right) : left;
      int second = orderedChopsticks ? Math.max(left, right) : right;
      philosophers[i] = new Philosopher(i, chopsticks[first], first, chopsticks[second], second);
      executor.execute(philosophers[i]);
    }

    // Let the simulation run, checking regularly whether the philosophers are deadlocked
    boolean deadlocked = false;
    long endTime = System.currentTimeMillis() + durationSeconds * 1000L;
    while (System.currentTimeMillis() < endTime && !deadlocked) {
      Thread.sleep(DEADLOCK_CHECK_INTERVAL_MS);
      deadlocked = isDeadlocked();
    }

    // Stop every philosopher (interrupts sleeping and waiting threads) and wait for them to exit
    executor.shutdownNow();
    executor.awaitTermination(5, TimeUnit.SECONDS);

    System.out.println();
    if (deadlocked) {
      System.out.println("DEADLOCK detected: every philosopher holds one chopstick and waits"
          + " for the next one.");
    } else {
      System.out.println("No deadlock occurred during the " + durationSeconds + " s run.");
    }
    System.out.println("Meals eaten:");
    for (Philosopher philosopher : philosophers) {
      System.out.println("  Philosopher " + philosopher.getId() + ": "
          + philosopher.getMealsEaten());
    }
  }

  /**
   * Uses the JVM's built-in deadlock detector, which also understands ReentrantLock.
   *
   * @return true if some threads are deadlocked
   */
  private static boolean isDeadlocked() {
    ThreadMXBean threadBean = ManagementFactory.getThreadMXBean();
    return threadBean.findDeadlockedThreads() != null;
  }

  /** A philosopher alternates between thinking and eating; eating needs both chopsticks. */
  public static class Philosopher implements Runnable {

    private final int id;
    private final ReentrantLock firstChopstick;
    private final int firstNumber;
    private final ReentrantLock secondChopstick;
    private final int secondNumber;
    // Written by the philosopher's thread, read by main at the end, so it must be thread-safe
    private final AtomicInteger mealsEaten = new AtomicInteger();

    /**
     * Creates a philosopher.
     *
     * @param id philosopher number
     * @param firstChopstick chopstick picked up first
     * @param firstNumber number of the first chopstick, used in the messages
     * @param secondChopstick chopstick picked up second
     * @param secondNumber number of the second chopstick, used in the messages
     */
    public Philosopher(int id, ReentrantLock firstChopstick, int firstNumber,
        ReentrantLock secondChopstick, int secondNumber) {
      this.id = id;
      this.firstChopstick = firstChopstick;
      this.firstNumber = firstNumber;
      this.secondChopstick = secondChopstick;
      this.secondNumber = secondNumber;
    }

    @Override
    public void run() {
      try {
        while (!Thread.currentThread().isInterrupted()) {
          doAction("is thinking");

          // lockInterruptibly (instead of lock) lets main stop a philosopher that is waiting
          firstChopstick.lockInterruptibly();
          try {
            log("picked up chopstick " + firstNumber);

            secondChopstick.lockInterruptibly();
            try {
              log("picked up chopstick " + secondNumber);
              int meal = mealsEaten.incrementAndGet();
              doAction("is eating (meal " + meal + ")");
            } finally {
              secondChopstick.unlock();
              log("put down chopstick " + secondNumber);
            }
          } finally {
            firstChopstick.unlock();
            log("put down chopstick " + firstNumber);
          }
        }
      } catch (InterruptedException e) {
        Thread.currentThread().interrupt(); // end of the simulation
      }
    }

    public int getId() {
      return id;
    }

    public int getMealsEaten() {
      return mealsEaten.get();
    }

    /**
     * Prints the action, then spends a random time (0 to MAX_ACTION_TIME_MS) doing it.
     *
     * @param action description of the action
     * @throws InterruptedException if the simulation is stopped
     */
    private void doAction(String action) throws InterruptedException {
      log(action);
      Thread.sleep((long) (Math.random() * MAX_ACTION_TIME_MS));
    }

    /**
     * Prints a message prefixed with the philosopher's number.
     *
     * @param message text to print
     */
    private void log(String message) {
      System.out.println("Philosopher " + id + " " + message);
    }
  }
}