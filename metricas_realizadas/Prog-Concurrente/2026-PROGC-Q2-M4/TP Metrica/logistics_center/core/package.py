"""Immutable domain value objects exchanged between threads."""

from dataclasses import dataclass


@dataclass(frozen=True, slots=True)
class Package:
  """A package unloaded from a truck.

  Frozen so it can be shared across threads without synchronization.

  Attributes:
    sku: Product identifier.
    units: Number of product units contained in the package.
    batch_id: Identifier of the truck batch that delivered it.
  """

  sku: str
  units: int
  batch_id: str
