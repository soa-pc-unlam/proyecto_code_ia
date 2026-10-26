"""Reader side (Rich dashboard). Intentionally empty until the UI phase.

The dashboard must only consume `Inventory.snapshot()` and `Queue.qsize()`;
it must never touch internal state or hold the inventory lock while rendering.
"""
