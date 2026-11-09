package ca.mcgill.ecse420.a2;

import java.util.concurrent.atomic.AtomicLong;

/**
 * Hands out "request tickets" used to measure overtaking in {@link OvertakingTest}.
 *
 * <p>A request ticket records when a thread started competing for the lock. The harness calls
 * {@link #begin()} right before {@code lock()}, which gives the thread a ticket. A lock may
 * <em>optionally</em> call {@link #mark()} at a point of its own choosing (for example, at the end
 * of the Bakery doorway, or after entering the first Filter level). This replaces the ticket with a
 * fresh one, so overtaking is then measured from that point instead of from the {@code lock()}
 * call.
 *
 * <p>Calling {@link #mark()} costs one atomic increment, which slightly changes the timing of the
 * lock. Mention this in the report if you use it.
 */
public final class Doorway {

  /** Global source of request tickets, strictly increasing within one run. */
  private static final AtomicLong ticketCounter = new AtomicLong();

  /** The current ticket of the calling thread, or -1 if it has none. */
  private static final ThreadLocal<Long> currentTicket = ThreadLocal.withInitial(() -> -1L);

  private Doorway() {
    // Utility class, not meant to be instantiated.
  }

  /** Restarts the ticket sequence. Called by the harness before each run. */
  static void reset() {
    ticketCounter.set(0);
  }

  /** Gives the calling thread a new ticket. Called by the harness just before {@code lock()}. */
  static void begin() {
    currentTicket.set(ticketCounter.getAndIncrement());
  }

  /**
   * Replaces the calling thread's ticket with a fresh one. Locks may call this to say "this thread
   * has now finished its doorway". Safe to call even when no overtaking test is running.
   */
  public static void mark() {
    currentTicket.set(ticketCounter.getAndIncrement());
  }

  /**
   * Returns the calling thread's ticket and clears it. Called by the harness inside the critical
   * section.
   *
   * @return the ticket from the latest {@link #begin()} or {@link #mark()}
   */
  static long end() {
    long ticket = currentTicket.get();
    currentTicket.set(-1L);
    return ticket;
  }
}
