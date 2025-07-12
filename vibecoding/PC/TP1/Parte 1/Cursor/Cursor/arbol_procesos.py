import multiprocessing
import time 
import os

# Pausa configurable para observar la creación de procesos
def pausa():
    time.sleep(1)  # Puedes cambiar a input('Presiona Enter para continuar...') si prefieres

def print_node(name):
    print(f"Nodo {name} (PID: {os.getpid()}, PPID: {os.getppid()})")
    pausa()

def node_e():
    print_node('E')
    h = multiprocessing.Process(target=print_node, args=('H',))
    i = multiprocessing.Process(target=print_node, args=('I',))
    h.start()
    i.start()
    h.join()
    i.join()

def node_c():
    print_node('C')
    e = multiprocessing.Process(target=node_e)
    e.start()
    e.join()

def node_d():
    print_node('D')
    f = multiprocessing.Process(target=print_node, args=('F',))
    g = multiprocessing.Process(target=print_node, args=('G',))
    f.start()
    g.start()
    f.join()
    g.join()

def node_b():
    print_node('B')
    c = multiprocessing.Process(target=node_c)
    d = multiprocessing.Process(target=node_d)
    c.start()
    d.start()
    c.join()
    d.join()

def main():
    print_node('A')
    b = multiprocessing.Process(target=node_b)
    b.start()
    b.join()
    print("\nÁrbol de procesos creado. Presiona Enter para finalizar.")
    input()

if __name__ == "__main__":
    multiprocessing.set_start_method('spawn')  # Recomendado en Windows
    main() 