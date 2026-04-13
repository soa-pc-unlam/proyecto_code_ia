import sys
import os
from classes import BattleshipClient
from constants import *

os.chdir(os.path.dirname(os.path.abspath(__file__)))
sys.path.append(os.path.dirname(__file__))
sys.dont_write_bytecode = True

def get_connection_config():
    # Banner
    print("\n" + "="*60)
    print("██████╗  █████╗ ████████╗ █████╗ ██╗     ██╗      █████╗ ")
    print("██╔══██╗██╔══██╗╚══██╔══╝██╔══██╗██║     ██║     ██╔══██╗")
    print("██████╔╝███████║   ██║   ███████║██║     ██║     ███████║")
    print("██╔══██╗██╔══██║   ██║   ██╔══██║██║     ██║     ██╔══██║")
    print("██████╔╝██║  ██║   ██║   ██║  ██║███████╗███████╗██║  ██║")
    print("╚═════╝ ╚═╝  ╚═╝   ╚═╝   ╚═╝  ╚═╝╚══════╝╚══════╝╚═╝  ╚═╝")
    print("███╗   ██╗ █████╗ ██╗   ██╗ █████╗ ██╗     ")
    print("████╗  ██║██╔══██╗██║   ██║██╔══██╗██║     ")
    print("██╔██╗ ██║███████║██║   ██║███████║██║     ")
    print("██║╚██╗██║██╔══██║╚██╗ ██╔╝██╔══██║██║     ")
    print("██║ ╚████║██║  ██║ ╚████╔╝ ██║  ██║███████╗")
    print("╚═╝  ╚═══╝╚═╝  ╚═╝  ╚═══╝  ╚═╝  ╚═╝╚══════╝")
    print("="*60 + "\n")
    
    while True:
        try:
            local_server = input("¿Tenes el servidor levantado en local? (s/N): ").strip().lower()
            
            if local_server in ['s', 'si', 'yes', 'y']:
                return DEFAULT_SERVER_HOST, DEFAULT_SERVER_PORT
            
            host_input = input(f"Host del servidor (default: {DEFAULT_SERVER_HOST}): ").strip()
            host = host_input if host_input else DEFAULT_SERVER_HOST
            
            port_input = input(f"Puerto del servidor (default: {DEFAULT_SERVER_PORT}): ").strip()
            port = int(port_input) if port_input else DEFAULT_SERVER_PORT
            
            return host, port
        except (KeyboardInterrupt, EOFError):
            sys.exit(0)

if __name__ == "__main__":
    try:
        host, port = get_connection_config()
        client = BattleshipClient()
        client.set_connection_params(host, port)
        client.run()
    except Exception as e:
        print(f"Error: {e}")
        sys.exit(1)