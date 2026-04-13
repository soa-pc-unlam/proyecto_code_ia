import threading
from typing import List

# Constants
ZERO: int = 0

Matrix = List[List[int]]


def multiply_row(row_index: int, matrix_a: Matrix, matrix_b: Matrix,
                 result: Matrix) -> None:
    """Multiplies one row of matrix_a with matrix_b and stores it in result."""
    num_cols_b: int = len(matrix_b[ZERO])
    num_cols_a: int = len(matrix_a[ZERO])
    for col in range(num_cols_b):
        result[row_index][col] = sum(
            matrix_a[row_index][k] * matrix_b[k][col] for k in range(num_cols_a)
        )


def multiply_matrices(matrix_a: Matrix, matrix_b: Matrix) -> Matrix:
    """Multiplies two matrices using concurrent threads."""
    num_rows_a: int = len(matrix_a)
    num_cols_b: int = len(matrix_b[ZERO])
    result: Matrix = [[ZERO] * num_cols_b for _ in range(num_rows_a)]
    threads: List[threading.Thread] = []

    for row in range(num_rows_a):
        thread = threading.Thread(
            target=multiply_row, args=(row, matrix_a, matrix_b, result)
        )
        threads.append(thread)
        thread.start()

    for thread in threads:
        thread.join()

    return result


if __name__ == "__main__":
    matrix_a: Matrix = [[1, 2, 3], [4, 5, 6]]
    matrix_b: Matrix = [[7, 8], [9, 10], [11, 12]]

    result_matrix: Matrix = multiply_matrices(matrix_a, matrix_b)
    print("Result")
    for row in result_matrix:
        print(row)
