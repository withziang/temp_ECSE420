"""Exhaustive model check of the Shaky/Flaky lock (assignment Fig. 1).

Every shared read or write is one atomic step (atomic registers, as in the
textbook proofs). Each thread runs lock(); CS; unlock() `rounds` times.
We explore every interleaving and check:
  * mutual exclusion: never two threads in the CS,
  * invariant: whenever a thread is in the CS, busy == true,
  * deadlock: a reachable state where no thread can ever enter the CS again.
"""

import sys
from collections import deque

# Program counters
W_TURN, R_BUSY, W_BUSY, R_TURN, CS, UNLOCK, DONE = range(7)


def step(state, t, n):
    """Returns the successor state when thread t takes one step, or None."""
    turn, busy, pcs, left = state
    pc = pcs[t]
    pcs = list(pcs)
    left = list(left)
    if pc == W_TURN:  # line 8: turn = me
        turn = t
        pcs[t] = R_BUSY
    elif pc == R_BUSY:  # line 9: while (busy)
        pcs[t] = W_TURN if busy else W_BUSY
    elif pc == W_BUSY:  # line 10: busy = true
        busy = True
        pcs[t] = R_TURN
    elif pc == R_TURN:  # line 11: while (turn != me)
        pcs[t] = CS if turn == t else W_TURN
    elif pc == CS:
        pcs[t] = UNLOCK
    elif pc == UNLOCK:  # line 14: busy = false
        busy = False
        left[t] -= 1
        pcs[t] = W_TURN if left[t] > 0 else DONE
    else:
        return None
    return (turn, busy, tuple(pcs), tuple(left))


def explore(n, rounds):
    start = (0, False, tuple([W_TURN] * n), tuple([rounds] * n))
    seen = {start: None}
    queue = deque([start])
    edges = {}
    while queue:
        s = queue.popleft()
        in_cs = [t for t in range(n) if s[2][t] == CS]
        assert len(in_cs) <= 1, f"mutual exclusion violated in {s}"
        assert not in_cs or s[1], f"thread in CS with busy == false: {s}"
        succ = []
        for t in range(n):
            nxt = step(s, t, n)
            if nxt is not None:
                succ.append(nxt)
                if nxt not in seen:
                    seen[nxt] = (s, t)
                    queue.append(nxt)
        edges[s] = succ

    # Deadlock: a state from which no CS entry is reachable, while some
    # thread still wants to enter.
    can_enter = {s for s in edges if CS in s[2]}
    changed = True
    while changed:
        changed = False
        for s, succ in edges.items():
            if s not in can_enter and any(x in can_enter for x in succ):
                can_enter.add(s)
                changed = True
    # Stuck = some thread still wants the lock, but no CS entry is reachable.
    stuck = [s for s in edges
             if s not in can_enter and any(p != DONE for p in s[2])]
    return len(seen), stuck, seen


def trace(seen, s):
    path = []
    while seen[s] is not None:
        prev, t = seen[s]
        path.append((t, prev, s))
        s = prev
    return list(reversed(path))


if __name__ == "__main__":
    names = "AB C".replace(" ", "")
    for n, rounds in [(2, 3), (3, 2)]:
        count, stuck, seen = explore(n, rounds)
        print(f"n={n}, {rounds} acquisitions each: {count} states, "
              f"mutual exclusion and 'busy is true in CS' hold in all of them; "
              f"{len(stuck)} deadlocked states")
    # Shortest trace into a deadlocked state for 2 threads.
    count, stuck, seen = explore(2, 1)
    shortest = min(stuck, key=lambda s: len(trace(seen, s)))
    label = ["turn = me", "read busy", "busy = true", "read turn",
             "in CS", "unlock", "done"]
    print("\nShortest path to deadlock (2 threads):")
    for t, prev, s in trace(seen, shortest):
        print(f"  {'AB'[t]}: {label[prev[2][t]]:<11} -> turn={'AB'[s[0]]}, busy={s[1]}")
    print("Final state:", shortest)
