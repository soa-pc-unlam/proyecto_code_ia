import asyncio
import sys
import os

sys.dont_write_bytecode = True

sys.path.append(os.path.join(os.path.dirname(__file__), 'classes'))

from battleship_server import BattleshipServer

async def main():
    server = BattleshipServer()
    try:
        await server.start_server()
    except KeyboardInterrupt:
        print("Servidor detenido por el usuario")
    except Exception as e:
        print(f"Error en el servidor: {e}")
        raise

if __name__ == "__main__":
    asyncio.run(main())