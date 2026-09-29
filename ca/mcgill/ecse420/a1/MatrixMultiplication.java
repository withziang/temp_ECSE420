package ca.mcgill.ecse420.a1;

import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

public class MatrixMultiplication {

  private static final int NUMBER_THREADS = 1;
  private static final int MATRIX_SIZE = 2000;
  private static final int REPETITIONS = 3;

  public static void main(String[] args) {
    if (args.length > 0 && args[0].equals("threads")) {
      // Question 1.4: java ca.mcgill.ecse420.a1.MatrixMultiplication threads [size]
      int sz = args.length > 1 ? Integer.parseInt(args[1]) : 4000;
      threadCountExperiment(sz);
      return;
    }
    if (args.length > 0 && args[0].equals("sizes")) {
      // Question 1.5: java ca.mcgill.ecse420.a1.MatrixMultiplication sizes [threads]
      int th = args.length > 1 ? Integer.parseInt(args[1]) : NUMBER_THREADS;
      matrixSizeExperiment(th);
      return;
    }
    // Generate two random matrices, same size
    double[][] a = generateRandomMatrix(MATRIX_SIZE, MATRIX_SIZE);
    double[][] b = generateRandomMatrix(MATRIX_SIZE, MATRIX_SIZE);
    measureExecutionTime(a, b);
  }

  /**
   * Returns the result of a sequential matrix multiplication
   * The two matrices are randomly generated
   * @param a is the first matrix
   * @param b is the second matrix
   * @return the result of the multiplication
   * */
  public static double[][] sequentialMultiplyMatrix(double[][] a, double[][] b) {
    int nRows = a.length;      // rows of a -> rows of the result
    int nInner = a[0].length;  // columns of a = rows of b -> length of each dot product
    int nCols = b[0].length;   // columns of b -> columns of the result
    double[][] r = new double[nRows][nCols];

    // standard triple nested loop: r[i][j] = sum over k of a[i][k] * b[k][j]
    for (int i = 0; i < nRows; i++) {
      for (int j = 0; j < nCols; j++) {
        double sum = 0.0;
        for (int k = 0; k < nInner; k++) {
          sum += a[i][k] * b[k][j];
        }
        r[i][j] = sum;
      }
    }
    return r;
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
   * Same as parallelMultiplyMatrix(a, b) but with an explicit number of threads,
   * so the thread count can be varied in the experiments.
   * @param a is the first matrix
   * @param b is the second matrix
   * @param threads number of threads to use
   * @return the result of the multiplication
   */
  public static double[][] parallelMultiplyMatrix(double[][] a, double[][] b, int threads) {
    int nRows = a.length;
    int nInner = a[0].length;
    int nCols = b[0].length;
    double[][] r = new double[nRows][nCols];

    // Never use more threads than there are rows to hand out
    int nThreads = Math.min(threads, nRows);
    ExecutorService ex = Executors.newFixedThreadPool(nThreads);

    // Split the rows of r into contiguous blocks, one block per thread.
    // If the rows do not divide evenly, the first (nRows % nThreads) blocks get one extra row.
    int rowsPer = nRows / nThreads;
    int extra = nRows % nThreads;
    int next = 0;
    for (int t = 0; t < nThreads; t++) {
      final int first = next;
      final int last = next + rowsPer + (t < extra ? 1 : 0); // exclusive
      next = last;

      // Each task computes only rows [first, last) of r.
      // Rows are disjoint between tasks, so no synchronization on 'r' is needed.
      ex.execute(() -> {
        for (int i = first; i < last; i++) {
          for (int j = 0; j < nCols; j++) {
            double sum = 0.0;
            for (int k = 0; k < nInner; k++) {
              sum += a[i][k] * b[k][j];
            }
            r[i][j] = sum;
          }
        }
      });
    }

    // Stop accepting tasks and wait until every submitted task has finished
    ex.shutdown();
    try {
      ex.awaitTermination(1, TimeUnit.HOURS);
    } catch (InterruptedException e) {
      Thread.currentThread().interrupt();
    }
    return r;
  }

  /**
   * Populates a matrix of given size with randomly generated integers between 0-10.
   * @param numRows number of rows
   * @param numCols number of cols
   * @return matrix
   */
  private static double[][] generateRandomMatrix (int numRows, int numCols) {
    double matrix[][] = new double[numRows][numCols];
    for (int row = 0 ; row < numRows ; row++ ) {
      for (int col = 0 ; col < numCols ; col++ ) {
        matrix[row][col] = (double) ((int) (Math.random() * 10.0));
      }
    }
    return matrix;
  }

  /**
   * Measures and prints the execution time of the sequential and the parallel
   * matrix multiplication of a and b.
   * @param a is the first matrix
   * @param b is the second matrix
   * @return array of two values: {sequential time, parallel time} in milliseconds
   */
  public static double[] measureExecutionTime(double[][] a, double[][] b) {
    long t0 = System.nanoTime();
    sequentialMultiplyMatrix(a, b);
    long t1 = System.nanoTime();
    double seqMs = (t1 - t0) / 1_000_000.0;

    t0 = System.nanoTime();
    parallelMultiplyMatrix(a, b);
    t1 = System.nanoTime();
    double parMs = (t1 - t0) / 1_000_000.0;

    System.out.printf("Matrix size %dx%d, %d thread(s)%n", a.length, b[0].length, NUMBER_THREADS);
    System.out.printf("  Sequential: %.2f ms%n", seqMs);
    System.out.printf("  Parallel:   %.2f ms%n", parMs);
    return new double[] {seqMs, parMs};
  }

  /**
   * Question 1.4: multiplies two size x size matrices in parallel with 1, 2, 4, 8, 16 and 32
   * threads and prints the average execution time and speed-up for each thread count.
   * @param size number of rows and columns of the matrices
   */
  public static void threadCountExperiment(int size) {
    int[] counts = {1, 2, 4, 8, 16, 32};
    double[][] a = generateRandomMatrix(size, size);
    double[][] b = generateRandomMatrix(size, size);

    System.out.println("Question 1.4: " + size + "x" + size + " matrices, "
        + Runtime.getRuntime().availableProcessors() + " available processors");
    System.out.println("threads,time_ms,speedup");
    double base = 0; // time with 1 thread
    for (int th : counts) {
      double avg = averageTimeMs(a, b, true, th);
      if (th == 1) {
        base = avg;
      }
      System.out.printf("%d,%.2f,%.2f%n", th, avg, base / avg);
    }
  }

  /**
   * Question 1.5: multiplies square matrices of increasing size both sequentially and in
   * parallel (with the given number of threads) and prints the average time of each.
   * @param threads number of threads for the parallel method (the best count found in 1.4)
   */
  public static void matrixSizeExperiment(int threads) {
    int[] sizes = {100, 200, 500, 1000, 2000, 3000, 4000};

    System.out.println("Question 1.5: parallel method uses " + threads + " threads");
    System.out.println("size,sequential_ms,parallel_ms,speedup");
    for (int sz : sizes) {
      double[][] a = generateRandomMatrix(sz, sz);
      double[][] b = generateRandomMatrix(sz, sz);
      double seq = averageTimeMs(a, b, false, threads);
      double par = averageTimeMs(a, b, true, threads);
      System.out.printf("%d,%.2f,%.2f,%.2f%n", sz, seq, par, seq / par);
    }
  }

  /**
   * Times one multiplication method: one un-timed warm-up run, then the average of
   * REPETITIONS timed runs, in milliseconds.
   * @param a is the first matrix
   * @param b is the second matrix
   * @param parallel true to time parallelMultiplyMatrix, false to time sequentialMultiplyMatrix
   * @param threads number of threads (only used when parallel is true)
   * @return average execution time in milliseconds
   */
  private static double averageTimeMs(double[][] a, double[][] b, boolean parallel, int threads) {
    double tot = 0;
    for (int rep = 0; rep <= REPETITIONS; rep++) { // rep 0 is the warm-up
      long t0 = System.nanoTime();
      if (parallel) {
        parallelMultiplyMatrix(a, b, threads);
      } else {
        sequentialMultiplyMatrix(a, b);
      }
      double el = (System.nanoTime() - t0) / 1_000_000.0;
      if (rep > 0) {
        tot += el;
      }
    }
    return tot / REPETITIONS;
  }
}