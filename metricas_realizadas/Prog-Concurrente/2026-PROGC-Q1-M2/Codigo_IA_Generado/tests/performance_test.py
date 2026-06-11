#!/usr/bin/env python3
"""
Simple performance test for the ticket system.

It sends many concurrent reservation requests and reports latency statistics.
Use this together with `docker compose stats` to record CPU and memory.
"""

import asyncio
import os
import statistics
import sys
import time
from dataclasses import dataclass
from typing import List

import aiohttp


@dataclass
class Result:
    success: bool
    status: int
    elapsed: float
    message: str


async def login(session: aiohttp.ClientSession, base_url: str, username: str) -> str:
    async with session.post(
        f"{base_url}/api/auth/login",
        json={"username": username, "password": "test123"},
    ) as resp:
        if resp.status != 200:
            raise RuntimeError(f"Login failed for {username}: {await resp.text()}")
        data = await resp.json()
        return data["access_token"]


async def get_available_seats(
    session: aiohttp.ClientSession,
    base_url: str,
    concert_id: int,
    token: str,
) -> List[int]:
    async with session.get(
        f"{base_url}/api/concerts/{concert_id}/seats",
        headers={"Authorization": f"Bearer {token}"},
    ) as resp:
        if resp.status != 200:
            raise RuntimeError(f"Could not load seats: {await resp.text()}")
        seats = await resp.json()
        return [seat["id"] for seat in seats if seat["status"] == "available"]


async def reserve_one(
    session: aiohttp.ClientSession,
    base_url: str,
    concert_id: int,
    token: str,
    seat_id: int,
) -> Result:
    start = time.perf_counter()
    async with session.post(
        f"{base_url}/api/seats/reserve",
        headers={"Authorization": f"Bearer {token}"},
        json={"concert_id": concert_id, "seat_ids": [seat_id]},
    ) as resp:
        elapsed = time.perf_counter() - start
        try:
            body = await resp.json()
        except Exception:
            body = {"detail": await resp.text()}
        return Result(
            success=resp.status == 200,
            status=resp.status,
            elapsed=elapsed,
            message=body.get("message") or body.get("detail", ""),
        )


def percentile(values: List[float], pct: float) -> float:
    if not values:
        return 0.0
    ordered = sorted(values)
    index = min(len(ordered) - 1, int(round((pct / 100) * (len(ordered) - 1))))
    return ordered[index]


async def main():
    base_url = sys.argv[1] if len(sys.argv) > 1 else os.getenv(
        "BASE_URL",
        "http://localhost:8000",
    )
    concert_id = int(os.getenv("CONCERT_ID", "1"))
    total_requests = int(os.getenv("TOTAL_REQUESTS", "100"))
    concurrency = int(os.getenv("CONCURRENCY", "25"))

    connector = aiohttp.TCPConnector(limit=concurrency)
    async with aiohttp.ClientSession(connector=connector) as session:
        tokens = [
            await login(session, base_url, f"testuser{i}")
            for i in range(1, 11)
        ]
        available = await get_available_seats(
            session,
            base_url,
            concert_id,
            tokens[0],
        )
        if len(available) < total_requests:
            total_requests = len(available)

        semaphore = asyncio.Semaphore(concurrency)

        async def guarded_reserve(index: int) -> Result:
            async with semaphore:
                token = tokens[index % len(tokens)]
                seat_id = available[index]
                return await reserve_one(session, base_url, concert_id, token, seat_id)

        start = time.perf_counter()
        results = await asyncio.gather(
            *(guarded_reserve(i) for i in range(total_requests))
        )
        total_elapsed = time.perf_counter() - start

    latencies = [result.elapsed for result in results]
    successes = sum(1 for result in results if result.success)
    failures = len(results) - successes
    throughput = len(results) / total_elapsed if total_elapsed > 0 else 0

    print("=" * 60)
    print("Performance test - Ticket System")
    print("=" * 60)
    print(f"Base URL: {base_url}")
    print(f"Concert ID: {concert_id}")
    print(f"Requests: {len(results)}")
    print(f"Concurrency: {concurrency}")
    print(f"Total time: {total_elapsed:.3f}s")
    print(f"Throughput: {throughput:.2f} req/s")
    print(f"Successes: {successes}")
    print(f"Failures: {failures}")
    print(f"Avg latency: {statistics.mean(latencies):.4f}s")
    print(f"Min latency: {min(latencies):.4f}s")
    print(f"Max latency: {max(latencies):.4f}s")
    print(f"P95 latency: {percentile(latencies, 95):.4f}s")


if __name__ == "__main__":
    asyncio.run(main())
