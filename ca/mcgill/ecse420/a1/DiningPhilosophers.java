package ca.mcgill.ecse420.a1;

import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.locks.ReentrantLock;

public class DiningPhilosophers {

	private static final int MAX_ACTION_TIME_MS = 10;

	public static void main(String[] args) {

		int numberOfPhilosophers = args.length > 0 ? Integer.parseInt(args[0]) : 5;
		Philosopher[] philosophers = new Philosopher[numberOfPhilosophers];
		ReentrantLock[] chopsticks = new ReentrantLock[numberOfPhilosophers];

		for (int i = 0; i < numberOfPhilosophers; i++) {
			// Fair lock
			chopsticks[i] = new ReentrantLock(true);
		}

		ExecutorService executor = Executors.newFixedThreadPool(numberOfPhilosophers);
		for (int i = 0; i < numberOfPhilosophers; i++) {
			int left = i;
			int right = (i + 1) % numberOfPhilosophers;
			philosophers[i] = new Philosopher(i, chopsticks[Math.min(left, right)],
					chopsticks[Math.max(left, right)]);
			executor.execute(philosophers[i]);
		}
		executor.shutdown();
	}

	public static class Philosopher implements Runnable {

		private final int id;
		private final ReentrantLock firstChopstick;
		private final ReentrantLock secondChopstick;
		private int mealsEaten = 0;

		public Philosopher(int id, ReentrantLock firstChopstick, ReentrantLock secondChopstick) {
			this.id = id;
			this.firstChopstick = firstChopstick;
			this.secondChopstick = secondChopstick;
		}

		@Override
		public void run() {
			try {
				while (true) {
					doAction("is thinking");

					firstChopstick.lock();
					try {
						log("picked up first chopstick");

						secondChopstick.lock();
						try {
							log("picked up second chopstick");
							mealsEaten++;
							doAction("is eating (meal " + mealsEaten + ")");
						} finally {
							secondChopstick.unlock();
							log("put down second chopstick");
						}
					} finally {
						firstChopstick.unlock();
						log("put down first chopstick");
					}
				}
			} catch (InterruptedException e) {
				Thread.currentThread().interrupt();
			}
		}

		private void doAction(String action) throws InterruptedException {
			log(action);
			Thread.sleep((long) (Math.random() * MAX_ACTION_TIME_MS));
		}

		private void log(String message) {
			System.out.println("Philosopher " + id + " " + message);
		}
	}

}
