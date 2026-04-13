# server.py
# Juego de preguntas y respuestas por sockets.
# Ejecutar: python server.py

import socket
import threading
import json
import time
import queue
import traceback

HOST = "0.0.0.0"
PORT = 50007
MAX_PLAYERS = 10
WAIT_SECONDS = 60
NUM_QUESTIONS = 5
QUESTION_DURATION = 20  # segundos por pregunta

QUESTION_BANK = [
    {"text": "¿Cuál es la capital de Francia?", "options": ["Berlín", "Londres", "París", "Roma"], "answer": 2},
    {"text": "¿Cuánto es 2 + 2?", "options": ["3", "4", "22", "5"], "answer": 1},
    {"text": "¿Qué lenguaje usamos en este proyecto?", "options": ["Java", "C", "Python", "Ruby"], "answer": 2},
    {"text": "¿Qué color resulta de mezclar rojo y azul?", "options": ["Verde", "Morado", "Amarillo", "Naranja"], "answer": 1},
    {"text": "¿Cuál es el planeta más cercano al Sol?", "options": ["Venus", "Tierra", "Mercurio", "Marte"], "answer": 2},
]

clients_lock = threading.Lock()
# clients: conn -> {"name": str, "score": int, "addr": (ip,port)}
clients = {}

# Cola productor-consumidor: (conn, name, question_index, choice_int, recv_time)
answers_queue = queue.Queue()

server_running = True


def send_json(sock, obj):
    try:
        data = json.dumps(obj) + "\n"
        sock.sendall(data.encode("utf-8"))
    except Exception:
        # si falla enviar, ignoramos: cliente probablemente desconectado
        pass


def broadcast(obj):
    with clients_lock:
        for conn in list(clients.keys()):
            send_json(conn, obj)


def client_thread(conn, addr):
    buffer = ""
    name = None
    conn.settimeout(1.0)
    try:
        while server_running:
            try:
                data = conn.recv(4096)
                if not data:
                    break
                buffer += data.decode("utf-8")
                while "\n" in buffer:
                    line, buffer = buffer.split("\n", 1)
                    if not line.strip():
                        continue
                    try:
                        msg = json.loads(line)
                    except Exception:
                        continue
                    mtype = msg.get("type")
                    if mtype == "register":
                        # cliente solicita registro con nombre
                        name_candidate = str(msg.get("name", "")).strip()[:32] or f"user_{addr[1]}"
                        with clients_lock:
                            if len(clients) >= MAX_PLAYERS:
                                # sala llena: rechazar
                                send_json(conn, {"type": "full", "msg": "Sala llena"})
                                try:
                                    conn.close()
                                except:
                                    pass
                                return
                            # registrar
                            clients[conn] = {"name": name_candidate, "score": 0, "addr": addr}
                            name = name_candidate
                        send_json(conn, {"type": "registered", "name": name})
                        print(f"[SERVER] Registrado: {name} desde {addr}")
                    elif mtype == "answer":
                        # servidor registra tiempo de recepción (no confiar en cliente)
                        qidx = int(msg.get("question", -1))
                        try:
                            choice = int(msg.get("choice"))
                        except Exception:
                            continue
                        recv_time = time.time()
                        with clients_lock:
                            cname = clients[conn]["name"] if conn in clients else (name or "unknown")
                        # producer: encolar respuesta
                        answers_queue.put((conn, cname, qidx, choice, recv_time))
                    else:
                        # otros tipos no necesarios
                        pass
            except socket.timeout:
                continue
            except Exception:
                break
    except Exception:
        traceback.print_exc()
    finally:
        # limpiar cliente al desconectarse
        with clients_lock:
            if conn in clients:
                info = clients.pop(conn)
                print(f"[SERVER] Cliente desconectado: {info.get('name')} {addr}")
        try:
            conn.close()
        except:
            pass


def process_answers_for_question(qindex, qstart, duration, correct_index):
    """
    Drena la cola temporalmente, obtiene las respuestas válidas para qindex
    con recv_time <= qstart+duration, reencola las demás.
    Retorna lista de (conn, name, points_added).
    """
    temp = []
    try:
        while True:
            temp.append(answers_queue.get_nowait())
    except queue.Empty:
        pass

    valid = []
    others = []
    deadline = qstart + duration + 1e-6
    for (conn, name, qidx, choice, recv_time) in temp:
        if qidx == qindex and recv_time <= deadline:
            valid.append((conn, name, choice, recv_time))
        else:
            others.append((conn, name, qidx, choice, recv_time))

    # reencolar las respuestas que no correspondían a esta pregunta
    for itm in others:
        answers_queue.put(itm)

    updates = []
    for (conn, name, choice, recv_time) in valid:
        if choice == correct_index:
            elapsed = recv_time - qstart
            remaining_float = duration - elapsed
            remaining = int(max(0, remaining_float))
            points = 10 + remaining
        else:
            points = 0
        updates.append((conn, name, points))
    return updates


def game_thread():
    global server_running
    while server_running:
        # Esperar primer jugador
        first_wait_start = None
        while True:
            with clients_lock:
                player_count = len(clients)
            if player_count > 0:
                first_wait_start = time.time()
                break
            time.sleep(0.5)
            if not server_running:
                return

        # Sala de espera: enviar updates hasta que se llene o expire WAIT_SECONDS
        deadline = first_wait_start + WAIT_SECONDS
        while time.time() < deadline:
            with clients_lock:
                player_count = len(clients)
            remaining = int(max(0, deadline - time.time()))
            broadcast({"type": "wait", "players": player_count, "max": MAX_PLAYERS, "wait_seconds": remaining})
            if player_count >= MAX_PLAYERS:
                break
            time.sleep(1)

        # Tomar snapshot de participantes (conexiones actuales)
        with clients_lock:
            participants = list(clients.keys())
            # asegurar que todos tengan score inicial
            for conn in participants:
                if "score" not in clients[conn]:
                    clients[conn]["score"] = 0

        if not participants:
            # si nadie quedó, volver a esperar
            continue

        # Iniciar partida
        broadcast({"type": "start"})
        time.sleep(0.5)

        total_questions = min(NUM_QUESTIONS, len(QUESTION_BANK))
        for qidx in range(total_questions):
            q = QUESTION_BANK[qidx]
            question_msg = {
                "type": "question",
                "index": qidx,
                "text": q["text"],
                "options": q["options"],
                "duration": QUESTION_DURATION
            }
            question_start = time.time()
            # broadcast pregunta
            broadcast(question_msg)

            # esperar duración permitida (los clientes envían respuestas y el servidor las timestamp)
            time.sleep(QUESTION_DURATION)

            # procesar respuestas para esta pregunta
            updates = process_answers_for_question(qidx, question_start, QUESTION_DURATION, q["answer"])

            # aplicar puntajes
            with clients_lock:
                for (conn, name, pts) in updates:
                    if conn in clients:
                        clients[conn]["score"] += pts

                # preparar mapa de puntajes para enviar
                scores_map = {info["name"]: info["score"] for info in clients.values()}

            # enviar puntajes parciales
            broadcast({"type": "scores", "scores": scores_map})

            # pequeña pausa entre preguntas
            time.sleep(1.0)

        # Calcular podio
        with clients_lock:
            standings = sorted([(info["name"], info["score"]) for info in clients.values()], key=lambda x: -x[1])
        podium = standings[:3]
        broadcast({"type": "end", "podium": podium})

        # resetear puntajes para próxima partida
        with clients_lock:
            for info in clients.values():
                info["score"] = 0

        # descanso breve antes de la próxima partida
        time.sleep(3)


def accept_loop():
    global server_running
    s = socket.socket(socket.AF_INET, socket.SOCK_STREAM)
    s.setsockopt(socket.SOL_SOCKET, socket.SO_REUSEADDR, 1)
    s.bind((HOST, PORT))
    s.listen(16)
    print(f"[SERVER] Listening on {HOST}:{PORT}")
    threading.Thread(target=game_thread, daemon=True).start()
    try:
        while server_running:
            try:
                conn, addr = s.accept()
            except Exception:
                continue
            print(f"[SERVER] Connection from {addr}")
            threading.Thread(target=client_thread, args=(conn, addr), daemon=True).start()
    except KeyboardInterrupt:
        print("[SERVER] Shutting down...")
        server_running = False
    finally:
        s.close()


if __name__ == "__main__":
    accept_loop()

