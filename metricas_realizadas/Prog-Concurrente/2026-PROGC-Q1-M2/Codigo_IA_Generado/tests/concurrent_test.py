#!/usr/bin/env python3
"""
Concurrency test script for the ticket system.
Simulates multiple users trying to reserve the same seats simultaneously.
"""

import asyncio
import aiohttp
import time
import os
import sys
from typing import List, Dict
from dataclasses import dataclass


@dataclass
class TestResult:
    user_id: int
    seat_ids: List[int]
    success: bool
    message: str
    response_time: float


class TicketSystemTester:
    def __init__(self, base_url: str = "http://localhost:8000"):
        self.base_url = base_url
        self.session = None
        self.concert_id = None
        self.token = None
        self.user_tokens: Dict[int, str] = {}
        self.results: List[TestResult] = []

    async def setup(self):
        """Initialize the test session and get credentials."""
        self.session = aiohttp.ClientSession()
        print("🔐 Autenticando...")
        await self._login("testuser1", "test123")

    async def teardown(self):
        """Clean up the session."""
        if self.session:
            await self.session.close()

    async def _login(self, username: str, password: str):
        """Login and get JWT token."""
        url = f"{self.base_url}/api/auth/login"
        payload = {"username": username, "password": password}

        async with self.session.post(url, json=payload) as resp:
            if resp.status != 200:
                raise Exception(f"Login failed: {await resp.text()}")
            data = await resp.json()
            self.token = data["access_token"]
            token = self.token
            print(f"✅ Autenticado como {username}")

            return token

    async def _get_headers(self, token: str = None):
        """Get headers with JWT token."""
        return {"Authorization": f"Bearer {token or self.token}"}

    async def _ensure_user_tokens(self, num_users: int):
        """Authenticate several users so concurrent tests use distinct users."""
        for user_num in range(1, num_users + 1):
            if user_num not in self.user_tokens:
                self.user_tokens[user_num] = await self._login(
                    f"testuser{user_num}",
                    "test123",
                )

    async def select_concert(self, concert_id: int = 1):
        """Select a concert for the test."""
        self.concert_id = concert_id
        url = f"{self.base_url}/api/concerts/{concert_id}"
        headers = await self._get_headers()

        async with self.session.get(url, headers=headers) as resp:
            if resp.status != 200:
                raise Exception(f"Failed to get concert: {await resp.text()}")
            data = await resp.json()
            print(f"📅 Recital seleccionado: {data['name']}")

    async def get_available_seats(self) -> List[int]:
        """Get all available seats for the current concert."""
        url = f"{self.base_url}/api/concerts/{self.concert_id}/seats"
        headers = await self._get_headers()

        async with self.session.get(url, headers=headers) as resp:
            if resp.status != 200:
                raise Exception(f"Failed to get seats: {await resp.text()}")
            seats = await resp.json()
            available = [s["id"] for s in seats if s["status"] == "available"]
            print(f"💺 Asientos disponibles: {len(available)}")
            return available

    async def reserve_seats(
        self, user_id: int, seat_ids: List[int], token: str = None
    ) -> TestResult:
        """Try to reserve seats for a user."""
        url = f"{self.base_url}/api/seats/reserve"
        headers = await self._get_headers(token)
        payload = {
            "seat_ids": seat_ids,
            "concert_id": self.concert_id,
        }

        start_time = time.time()
        try:
            async with self.session.post(url, json=payload, headers=headers) as resp:
                elapsed = time.time() - start_time
                if resp.status == 200:
                    data = await resp.json()
                    return TestResult(
                        user_id=user_id,
                        seat_ids=seat_ids,
                        success=True,
                        message=data["message"],
                        response_time=elapsed,
                    )
                else:
                    error = await resp.json()
                    return TestResult(
                        user_id=user_id,
                        seat_ids=seat_ids,
                        success=False,
                        message=error.get("detail", "Unknown error"),
                        response_time=elapsed,
                    )
        except Exception as e:
            elapsed = time.time() - start_time
            return TestResult(
                user_id=user_id,
                seat_ids=seat_ids,
                success=False,
                message=str(e),
                response_time=elapsed,
            )

    async def test_concurrent_reservations(
        self, num_users: int = 10, seats_per_user: int = 2
    ):
        """
        Test concurrent seat reservations with multiple users trying to book
        overlapping seats.
        """
        print(f"\n🔄 Iniciando prueba de concurrencia...")
        print(f"   - Usuarios simultáneos: {num_users}")
        print(f"   - Asientos por usuario: {seats_per_user}")

        await self._ensure_user_tokens(num_users)
        available_seats = await self.get_available_seats()
        if len(available_seats) < seats_per_user * num_users:
            print(
                f"⚠️  Advertencia: No hay suficientes asientos "
                f"({len(available_seats)} < {seats_per_user * num_users})"
            )

        # Create tasks for concurrent reservations
        tasks = []
        for user_num in range(1, num_users + 1):
            # All users try to reserve the same first N seats
            seat_ids = available_seats[:seats_per_user]
            task = self.reserve_seats(
                user_num,
                seat_ids,
                self.user_tokens[user_num],
            )
            tasks.append(task)

        # Execute all reservations concurrently
        print(f"📤 Enviando {num_users} solicitudes concurrentes...")
        start_time = time.time()
        self.results = await asyncio.gather(*tasks)
        total_time = time.time() - start_time

        # Analyze results
        self._print_results(total_time)

    def _print_results(self, total_time: float):
        """Print test results summary."""
        successful = sum(1 for r in self.results if r.success)
        failed = len(self.results) - successful

        print(f"\n📊 Resultados de la prueba:")
        print(f"   Tiempo total: {total_time:.2f}s")
        print(f"   Reservas exitosas: {successful}")
        print(f"   Reservas fallidas: {failed}")
        print(f"   Tasa de éxito: {(successful/len(self.results)*100):.1f}%")

        print(f"\n📋 Detalle de reservas:")
        for i, result in enumerate(self.results, 1):
            status = "✅" if result.success else "❌"
            print(
                f"   {status} Usuario {result.user_id}: "
                f"{result.message} ({result.response_time:.3f}s)"
            )

        # Check for race conditions in logs
        print(f"\n🏁 Verificando condiciones de carrera...")
        if failed > 0:
            print(f"   ✓ Se detectaron {failed} condiciones de carrera (esperado)")
            print(f"   ✓ Solo {successful} usuario(s) pudieron reservar los asientos")
        else:
            print(f"   ⚠️  No se detectaron condiciones de carrera")

    async def test_sequential_scenario(self):
        """Test a scenario where users reserve different seats sequentially."""
        print(f"\n🔄 Iniciando prueba secuencial...")

        available_seats = await self.get_available_seats()
        num_users = min(5, len(available_seats) // 2)

        print(f"   - Usuarios: {num_users}")
        print(f"   - Asientos por usuario: 2")

        await self._ensure_user_tokens(num_users)
        tasks = []
        for user_num in range(1, num_users + 1):
            # Each user gets different seats
            start_idx = (user_num - 1) * 2
            seat_ids = available_seats[start_idx : start_idx + 2]
            if seat_ids:
                task = self.reserve_seats(
                    user_num,
                    seat_ids,
                    self.user_tokens[user_num],
                )
                tasks.append(task)

        print(f"📤 Enviando {len(tasks)} solicitudes...")
        start_time = time.time()
        self.results = await asyncio.gather(*tasks)
        total_time = time.time() - start_time

        self._print_results(total_time)


async def main():
    """Main test execution."""
    base_url = sys.argv[1] if len(sys.argv) > 1 else os.getenv(
        "BASE_URL",
        "http://localhost:8000",
    )

    print("=" * 60)
    print("🎤 Sistema de Venta de Entradas - Prueba de Concurrencia")
    print("=" * 60)

    tester = TicketSystemTester(base_url)

    try:
        await tester.setup()

        # Test 1: Select concert
        await tester.select_concert(concert_id=1)

        # Test 2: Concurrent reservations (same seats)
        await tester.test_concurrent_reservations(num_users=10, seats_per_user=1)

        # Test 3: Sequential different seats
        await tester.test_sequential_scenario()

        print(f"\n✅ Pruebas completadas exitosamente")

    except Exception as e:
        print(f"\n❌ Error durante las pruebas: {e}", file=sys.stderr)
        sys.exit(1)

    finally:
        await tester.teardown()


if __name__ == "__main__":
    asyncio.run(main())
