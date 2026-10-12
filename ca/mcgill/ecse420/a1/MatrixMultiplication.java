package ca.mcgill.ecse420.a1;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.concurrent.Callable;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;

public class MatrixMultiplication {

  // Number of threads used by parallelMultiplyMatrix(a, b).
  // Set this to the best thread count found in Question 1.4.
  private static final int NUMBER_THREADS = 32;
  private static final int MATRIX_SIZE = 2000;
  private static final int REPETITIONS = 3; // timed runs averaged per measurement

  public static void main(String[] args) {
    String mode = args.length > 0 ? args[0] : "";
    switch (mode) {
      case "validate" -> {
        boolean allPassed = validate();
        System.out.println(allPassed ? "ALL CHECKS PASSED" : "SOME CHECKS FAILED");
      }
      case "threads" -> {
        int matrixSize = args.length > 1 ? Integer.parseInt(args[1]) : 4000;
        threadCountExperiment(matrixSize);
      }
      case "sizes" -> {
        int threadCount = args.length > 1 ? Integer.parseInt(args[1]) : NUMBER_THREADS;
        matrixSizeExperiment(threadCount);
      }
      default -> {
        // Generate two random matrices, same size
        double[][] a = generateRandomMatrix(MATRIX_SIZE, MATRIX_SIZE);
        double[][] b = generateRandomMatrix(MATRIX_SIZE, MATRIX_SIZE);
        measureExecutionTime(a, b);
      }
    }
  }

  /**
   * Returns the result of a sequential matrix multiplication
   * The two matrices are randomly generated
   * @param a is the first matrix
   * @param b is the second matrix
   * @return the result of the multiplication
   * */
  public static double[][] sequentialMultiplyMatrix(double[][] a, double[][] b) {
    int numRows = a.length; // rows of a -> rows of the result
    int numCols = b[0].length; // columns of b -> columns of the result
    double[][] result = new double[numRows][numCols];

    // result[i][j] = sum over k of a[i][k] * b[k][j], computed one row of the result at a time
    for (int i = 0; i < numRows; i++) {
      multiplyRow(a, b, result, i);
    }
    return result;
  }

  /**
   * Returns the result of a concurrent matrix multiplication
   * The two matrices are randomly generated
   * @param a is the first matrix
   * @param b is the second matrix
   * @return the result of the multiplication
   * */
  public static double[][] parallelMultiplyMatrix(double[][] a, double[][] b) {
    return parallelMultiplyMatrix(a, b, NUMBER_THREADS);
  }

  /**
   * Same as parallelMultiplyMatrix(a, b) but with an explicit number of threads, so the thread
   * count can be varied in the experiments.
   *
   * @param a is the first matrix
   * @param b is the second matrix
   * @param requestedThreads number of threads to use
   * @return the result of the multiplication
   */
  public static double[][] parallelMultiplyMatrix(double[][] a, double[][] b,
      int requestedThreads) {
    int numRows = a.length;
    double[][] result = new double[numRows][b[0].length];

    // Never use more threads than there are rows to hand out
    int numThreads = Math.min(requestedThreads, numRows);
    ExecutorService executor = Executors.newFixedThreadPool(numThreads);

    // Split the rows of the result into contiguous blocks, one block per thread.
    // If the rows do not divide evenly, the first (numRows % numThreads) blocks get one extra row.
    int rowsPerThread = numRows / numThreads;
    int extraRows = numRows % numThreads;
    List<Callable<Void>> tasks = new ArrayList<>();
    int nextRow = 0;
    for (int t = 0; t < numThreads; t++) {
      final int firstRow = nextRow;
      final int endRow = nextRow + rowsPerThread + (t < extraRows ? 1 : 0); // exclusive
      nextRow = endRow;

      // Each task computes only rows [firstRow, endRow) of the result.
      // Row ranges are disjoint between tasks, so no synchronization on 'result' is needed.
      tasks.add(() -> {
        for (int i = firstRow; i < endRow; i++) {
          multiplyRow(a, b, result, i);
        }
        return null;
      });
    }

    try {
      // invokeAll blocks until every task has finished. Calling get() on each Future
      // (1) rethrows any exception thrown inside a task, and (2) guarantees that all writes
      // made by that task to 'result' are visible to this thread (happens-before).
      for (Future<Void> future : executor.invokeAll(tasks)) {
        future.get();
      }
    } catch (InterruptedException e) {
      Thread.currentThread().interrupt();
      throw new IllegalStateException("Interrupted during parallel multiplication", e);
    } catch (ExecutionException e) {
      throw new IllegalStateException("A multiplication task failed", e.getCause());
    } finally {
      executor.shutdown();
    }
    return result;
  }

  /**
   * Computes row i of the product a * b and stores it in result[i].
   *
   * <p>The loops are in i-k-j order: for each k, row k of b is multiplied by a[i][k] and added
   * to row i of the result. This reads b and the result row from left to right in memory, which
   * uses the CPU cache well. The textbook i-j-k order reads b down a column instead, jumping to a
   * different row of b on every step, which is several times slower for large matrices. Both
   * orders compute exactly the same values: result[i][j] = sum over k of a[i][k] * b[k][j].
   *
   * @param a is the first matrix
   * @param b is the second matrix
   * @param result matrix that receives the product; row i must be all zeros when called
   * @param i index of the row to compute
   */
  private static void multiplyRow(double[][] a, double[][] b, double[][] result, int i) {
    double[] resultRow = result[i];
    int innerSize = a[0].length; // columns of a = rows of b
    for (int k = 0; k < innerSize; k++) {
      double aik = a[i][k];
      double[] bRow = b[k];
      for (int j = 0; j < resultRow.length; j++) {
        resultRow[j] += aik * bRow[j];
      }
    }
  }

  /**
   * Populates a matrix of given size with randomly generated integers between 0-9.
   * @param numRows number of rows
   * @param numCols number of cols
   * @return matrix
   */
  private static double[][] generateRandomMatrix(int numRows, int numCols) {
    double[][] matrix = new double[numRows][numCols];
    for (int row = 0; row < numRows; row++) {
      for (int col = 0; col < numCols; col++) {
        matrix[row][col] = (double) ((int) (Math.random() * 10.0));
      }
    }
    return matrix;
  }

  /**
   * Measures and prints the execution time of the sequential and the parallel matrix
   * multiplication of a and b.
   *
   * @param a is the first matrix
   * @param b is the second matrix
   * @return array of two values: {sequential time, parallel time} in milliseconds
   */
  public static double[] measureExecutionTime(double[][] a, double[][] b) {
    long startTime = System.nanoTime();
    sequentialMultiplyMatrix(a, b);
    double sequentialMs = (System.nanoTime() - startTime) / 1_000_000.0;

    startTime = System.nanoTime();
    parallelMultiplyMatrix(a, b);
    double parallelMs = (System.nanoTime() - startTime) / 1_000_000.0;

    System.out.printf("Matrix size %dx%d, %d thread(s)%n", a.length, b[0].length,
        NUMBER_THREADS);
    System.out.printf("  Sequential: %.2f ms%n", sequentialMs);
    System.out.printf("  Parallel:   %.2f ms%n", parallelMs);
    return new double[] {sequentialMs, parallelMs};
  }

  /**
   * Question 1.4: multiplies two size x size matrices in parallel with 1, 2, 4, 8, 16 and 32
   * threads and prints the average execution time and speed-up for each thread count.
   *
   * @param size number of rows and columns of the matrices
   */
  public static void threadCountExperiment(int size) {
    int[] threadCounts = {1, 2, 4, 8, 16, 32};
    double[][] a = generateRandomMatrix(size, size);
    double[][] b = generateRandomMatrix(size, size);

    System.out.println("Question 1.4: " + size + "x" + size + " matrices, "
        + Runtime.getRuntime().availableProcessors() + " available processors");
    System.out.println("threads,time_ms,speedup");
    double oneThreadMs = 0; // time with 1 thread, the baseline for the speed-up
    for (int threadCount : threadCounts) {
      double averageMs = averageTimeMs(a, b, true, threadCount);
      if (threadCount == 1) {
        oneThreadMs = averageMs;
      }
      System.out.printf("%d,%.2f,%.2f%n", threadCount, averageMs, oneThreadMs / averageMs);
    }
  }

  /**
   * Question 1.5: multiplies square matrices of increasing size both sequentially and in
   * parallel (with the given number of threads) and prints the average time of each.
   *
   * @param threadCount number of threads for the parallel method (the best count found in 1.4)
   */
  public static void matrixSizeExperiment(int threadCount) {
    int[] sizes = {100, 200, 500, 1000, 2000, 3000, 4000};

    System.out.println("Question 1.5: parallel method uses " + threadCount + " threads");
    System.out.println("size,sequential_ms,parallel_ms,speedup");
    for (int size : sizes) {
      double[][] a = generateRandomMatrix(size, size);
      double[][] b = generateRandomMatrix(size, size);
      double sequentialMs = averageTimeMs(a, b, false, threadCount);
      double parallelMs = averageTimeMs(a, b, true, threadCount);
      System.out.printf("%d,%.2f,%.2f,%.2f%n", size, sequentialMs, parallelMs,
          sequentialMs / parallelMs);
    }
  }

  /**
   * Times one multiplication method: one un-timed warm-up run, then the average of REPETITIONS
   * timed runs, in milliseconds.
   *
   * @param a is the first matrix
   * @param b is the second matrix
   * @param parallel true to time parallelMultiplyMatrix, false to time sequentialMultiplyMatrix
   * @param threadCount number of threads (only used when parallel is true)
   * @return average execution time in milliseconds
   */
  private static double averageTimeMs(double[][] a, double[][] b, boolean parallel,
      int threadCount) {
    double totalMs = 0;
    for (int run = 0; run <= REPETITIONS; run++) { // run 0 is the warm-up
      long startTime = System.nanoTime();
      if (parallel) {
        parallelMultiplyMatrix(a, b, threadCount);
      } else {
        sequentialMultiplyMatrix(a, b);
      }
      double elapsedMs = (System.nanoTime() - startTime) / 1_000_000.0;
      if (run > 0) {
        totalMs += elapsedMs;
      }
    }
    return totalMs / REPETITIONS;
  }

  /**
   * Questions 1.1 and 1.2: checks both multiplication methods against hand-computed results,
   * algebraic identities, expected shapes, and each other.
   *
   * @return true if every check passed
   */
  public static boolean validate() {
    boolean allPassed = true;

    // 1. Hand-computed 2x2 product
    double[][] a2 = {{1, 2}, {3, 4}};
    double[][] b2 = {{5, 6}, {7, 8}};
    double[][] expected2 = {{19, 22}, {43, 50}};
    allPassed &= check("2x2 hand-computed (sequential)",
        Arrays.deepEquals(sequentialMultiplyMatrix(a2, b2), expected2));
    allPassed &= check("2x2 hand-computed (parallel, 2 threads)",
        Arrays.deepEquals(parallelMultiplyMatrix(a2, b2, 2), expected2));

    // 2. Hand-computed non-square 2x3 * 3x2 product
    double[][] a23 = {{1, 2, 3}, {4, 5, 6}};
    double[][] b32 = {{7, 8}, {9, 10}, {11, 12}};
    double[][] expected22 = {{58, 64}, {139, 154}};
    allPassed &= check("2x3 * 3x2 hand-computed (sequential)",
        Arrays.deepEquals(sequentialMultiplyMatrix(a23, b32), expected22));
    allPassed &= check("2x3 * 3x2 hand-computed (parallel, 2 threads)",
        Arrays.deepEquals(parallelMultiplyMatrix(a23, b32, 2), expected22));

    // 3. Algebraic identities on a random 50x50 matrix: M * I = M and M * 0 = 0
    double[][] matrix = generateRandomMatrix(50, 50);
    double[][] identity = new double[50][50];
    for (int i = 0; i < 50; i++) {
      identity[i][i] = 1;
    }
    double[][] zero = new double[50][50];
    allPassed &= check("M * I = M (sequential)",
        Arrays.deepEquals(sequentialMultiplyMatrix(matrix, identity), matrix));
    allPassed &= check("M * I = M (parallel, 4 threads)",
        Arrays.deepEquals(parallelMultiplyMatrix(matrix, identity, 4), matrix));
    allPassed &= check("M * 0 = 0 (sequential)",
        Arrays.deepEquals(sequentialMultiplyMatrix(matrix, zero), zero));
    allPassed &= check("M * 0 = 0 (parallel, 4 threads)",
        Arrays.deepEquals(parallelMultiplyMatrix(matrix, zero, 4), zero));

    // 4. Shape and parallel-vs-sequential comparison on a non-square 301x200 * 200x157 product.
    // 3 and 7 do not divide 301 (uneven split); 500 > 301 tests the cap on the thread count.
    double[][] a = generateRandomMatrix(301, 200);
    double[][] b = generateRandomMatrix(200, 157);
    double[][] sequentialResult = sequentialMultiplyMatrix(a, b);
    allPassed &= check("301x200 * 200x157 gives a 301x157 result",
        sequentialResult.length == 301 && sequentialResult[0].length == 157);
    for (int threadCount : new int[] {1, 2, 3, 7, 16, 500}) {
      allPassed &= check("parallel (" + threadCount + " threads) equals sequential",
          Arrays.deepEquals(parallelMultiplyMatrix(a, b, threadCount), sequentialResult));
    }
    return allPassed;
  }

  /**
   * Prints the outcome of one validation check.
   *
   * @param name description of the check
   * @param passed whether the check passed
   * @return passed, so results can be accumulated
   */
  private static boolean check(String name, boolean passed) {
    System.out.println((passed ? "PASS  " : "FAIL  ") + name);
    return passed;
  }
}