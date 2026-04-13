"""Concurrent Matrix Multiplication using Python Threads.

This module provides a function to multiply two matrices concurrently
using a thread for each row computation of the resulting matrix.
"""

import threading
from typing import List, Optional

# Type alias for clarity
Matrix = List[List[int]]


def _calculate_row(
    row_index: int, matrix_a: Matrix, matrix_b: Matrix, result_matrix: Matrix
) -> None:
  """Calculates a single row of the result matrix.

  Args:
    row_index: The index of the row to calculate in the result matrix.
    matrix_a: The first matrix (A).
    matrix_b: The second matrix (B).
    result_matrix: The matrix to store the result row.
  """
  num_cols_b = len(matrix_b[0])
  num_cols_a = len(matrix_a[0])
  row_a = matrix_a[row_index]

  for j in range(num_cols_b):
    sum_val = 0
    for k in range(num_cols_a):
      sum_val += row_a[k] * matrix_b[k][j]
    result_matrix[row_index][j] = sum_val


def _validate_matrices(matrix_a: Matrix, matrix_b: Matrix) -> bool:
  """Validates if matrices can be multiplied (A rows, B columns)."""
  if not matrix_a or not matrix_b:
    return False
  if not matrix_a[0] or not matrix_b[0]:
    return False
  return len(matrix_a[0]) == len(matrix_b)


def threaded_matrix_multiplication(
    matrix_a: Matrix, matrix_b: Matrix
) -> Optional[Matrix]:
  """Multiplies two matrices using one thread per row of the result matrix.

  Args:
    matrix_a: The first matrix (A), dimensions (M x N).
    matrix_b: The second matrix (B), dimensions (N x P).

  Returns:
    The resulting matrix C (M x P) or None if multiplication is invalid.
  """
  if not _validate_matrices(matrix_a, matrix_b):
    print('Error: Invalid matrix dimensions for multiplication.')
    return None

  num_rows_a = len(matrix_a)
  num_cols_b = len(matrix_b[0])
  result_matrix = [[0] * num_cols_b for _ in range(num_rows_a)]

  threads = []
  for i in range(num_rows_a):
    thread = threading.Thread(
        target=_calculate_row,
        args=(i, matrix_a, matrix_b, result_matrix))
    threads.append(thread)
    thread.start()

  for thread in threads:
    thread.join()

  return result_matrix


def main() -> None:
  """Main function to demonstrate concurrent matrix multiplication."""
  matrix_a: Matrix = [
      [1, 2],
      [3, 4],
      [5, 6]
  ]
  matrix_b: Matrix = [
      [7, 8, 9],
      [10, 11, 12]
  ]

  print('Matrix A:')
  for row in matrix_a:
    print(row)
  print('\nMatrix B:')
  for row in matrix_b:
    print(row)

  result = threaded_matrix_multiplication(matrix_a, matrix_b)

  if result is not None:
    num_threads = len(matrix_a)
    print(f'\nResult Matrix (C = A * B) [Using {num_threads} Threads]:')
    for row in result:
      print(row)


if __name__ == '__main__':
  main()