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
			chopsticks[i] = new ReentrantLock();
		}

		ExecutorService executor = Executors.newFixedThreadPool(numberOfPhilosophers);
		for (int i = 0; i < numberOfPhilosophers; i++) {
			philosophers[i] = new Philosopher(i, chopsticks[i], chopsticks[(i + 1) % numberOfPhilosophers]);
			executor.execute(philosophers[i]);
		}
		executor.shutdown();
	}

	public static class Philosopher implements Runnable {

		private final int id;
		private final ReentrantLock leftChopstick;
		private final ReentrantLock rightChopstick;

		public Philosopher(int id, ReentrantLock leftChopstick, ReentrantLock rightChopstick) {
			this.id = id;
			this.leftChopstick = leftChopstick;
			this.rightChopstick = rightChopstick;
		}

		@Override
		public void run() {
			try {
				while (true) {
					doAction("is thinking");

					leftChopstick.lock();
					try {
						log("picked up left chopstick");

						rightChopstick.lock();
						try {
							log("picked up right chopstick");
							doAction("is eating");
						} finally {
							rightChopstick.unlock();
							log("put down right chopstick");
						}
					} finally {
						leftChopstick.unlock();
						log("put down left chopstick");
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
