"""
Quiz Game (Server + Client) using sockets, threads and a producer-consumer style answer queue.
Files contained in this single source doc (separate into server.py and client.py):

- server.py: Run this first. Accepts up to 10 clients, waits until 10 players or WAIT_SECONDS to start.
- client.py: GUI client using tkinter. Run one per player (on Windows). Connects to server, shows screens: name entry, waiting room, questions, podium.

Protocol (JSON messages UTF-8, newline-terminated):
- Client -> Server
    {"type":"register","name": string}
    {"type":"answer","question": int, "choice": int (0..3), "remaining": float}
- Server -> Client
    {"type":"wait","players":int, "max":int, "wait_seconds":int}
    {"type":"start"}
    {"type":"question","index":int, "text":str, "options":[str,str,str,str], "duration":int}
    {"type":"scores","scores": {name: points, ...}}
    {"type":"end","podium":[[name,points], ...]}
    {"type":"info","msg":str}

How scoring works in this implementation:
- A correct answer gives base 10 points.
- Among the players who answered correctly for that question, the fastest (smallest response time -> largest "remaining" seconds) wins the time bonus: +remaining_seconds (rounded down).
- If multiple players tie for fastest remaining seconds, all tied receive the bonus.

Adjustable parameters in server: MAX_PLAYERS (<=10), WAIT_SECONDS (seconds to wait if < max), QUESTION_DURATION (8), NUM_QUESTIONS (5).

Note: This code is meant as an educational working prototype. For production you'd add more error handling, reconnection, persistence, security.
"""

# ----------------- server.py -----------------
# Run: python server.py

import socket
import threading
import json
import time
import queue

HOST = '0.0.0.0'
PORT = 50007
MAX_PLAYERS = 10
WAIT_SECONDS = 80  # time to wait before starting if not full
QUESTION_DURATION = 20
NUM_QUESTIONS = 3

# Simple question bank (5 questions). You can expand or load from file.
QUESTION_BANK = [
    {"text":"¿Cuál es la capital de Francia?","options":["Berlín","Londres","París","Roma"],"answer":2},
    {"text":"¿Cuánto es 2 + 2?","options":["3","4","22","5"],"answer":1},
    {"text":"¿Qué lenguaje usamos en este proyecto?","options":["Java","C","Python","Ruby"],"answer":2},
    {"text":"¿Qué color resulta de mezclar rojo y azul?","options":["Verde","Morado","Amarillo","Naranja"],"answer":1},
    {"text":"¿Cuál es el planeta más cercano al Sol?","options":["Venus","Tierra","Mercurio","Marte"],"answer":2},
]

clients_lock = threading.Lock()
clients = {}  # socket -> {"name":..., "score":int, "socket":sock}
answers_queue = queue.Queue()  # producer-consumer: client threads produce answers here

server_running = True

def send_json(sock, obj):
    try:
        data = json.dumps(obj) + "\n"
        sock.sendall(data.encode('utf-8'))
    except Exception as e:
        print('send error', e)


def broadcast(obj):
    with clients_lock:
        for entry in list(clients.values()):
            try:
                send_json(entry['socket'], obj)
            except Exception as e:
                print('broadcast send error', e)


def client_thread(conn, addr):
    print('Client connected', addr)
    name = None
    buff = ''
    conn.settimeout(1.0)
    try:
        while server_running:
            try:
                data = conn.recv(4096)
                if not data:
                    break
                buff += data.decode('utf-8')
                while "\n" in buff:
                    line, buff = buff.split('\n', 1)
                    if not line.strip():
                        continue
                    msg = json.loads(line)
                    if msg.get('type') == 'register':
                        name = msg.get('name')[:32]
                        with clients_lock:
                            clients[conn] = {'name': name, 'score': 0, 'socket': conn}
                        print('Registered', name)
                    elif msg.get('type') == 'answer':
                        # push into answers queue with socket reference
                        answers_queue.put((conn, msg))
                    else:
                        print('Unknown msg', msg)
            except socket.timeout:
                continue
            except Exception as e:
                print('client recv error', e)
                break
    finally:
        print('Client disconnected', addr, name)
        with clients_lock:
            if conn in clients:
                del clients[conn]
        conn.close()


def answers_consumer(question_index, question_start_time, correct_index, duration):
    """
    Collect answers for a question until duration expires (seconds). Then compute scores and return list of per-client data.
    This function consumes items from answers_queue that match the question_index and were received in the duration window.
    """
    collected = []  # list of (conn, name, choice, remaining)
    deadline = question_start_time + duration
    while time.time() < deadline + 0.1:
        try:
            conn, msg = answers_queue.get(timeout=0.1)
        except queue.Empty:
            continue
        # validate message
        if msg.get('question') != question_index:
            # ignore answers for other questions (or stale)
            continue
        choice = int(msg.get('choice'))
        remaining = float(msg.get('remaining'))
        with clients_lock:
            if conn not in clients:
                continue
            name = clients[conn]['name']
        collected.append((conn, name, choice, remaining))
    # evaluate
    # find correct answers and fastest remaining among correct
    corrects = [(c,n,choice,rem) for (c,n,choice,rem) in collected if choice == correct_index]
    if corrects:
        # fastest == max remaining (more leftover seconds)
        max_rem = max(rem for (_,_,_,rem) in corrects)
        # tie possible
        winners = [(c,n,choice,rem) for (c,n,choice,rem) in corrects if abs(rem - max_rem) < 1e-6]
    else:
        winners = []
    # prepare score updates
    updates = []  # (conn,name,points_added)
    for (c,n,choice,rem) in collected:
        if choice == correct_index:
            base = 10
            bonus = 0
            # if in winners add floor(rem)
            if any(c is w[0] for w in winners):
                bonus = int(rem)  # remaining seconds as int
            updates.append((c,n, base + bonus))
        else:
            updates.append((c,n,0))
    return updates


def game_thread():
    print('Game thread started')
    global server_running
    while server_running:
        # waiting room: wait until MAX_PLAYERS or WAIT_SECONDS
        start_time = time.time()
        deadline = start_time + WAIT_SECONDS
        while time.time() < deadline:
            with clients_lock:
                count = len(clients)
            broadcast({'type':'wait','players':count,'max':MAX_PLAYERS,'wait_seconds':int(deadline-time.time())})
            if count >= MAX_PLAYERS:
                break
            time.sleep(1)
        with clients_lock:
            players_snapshot = list(clients.items())  # list of (conn, {'name', 'score', 'socket'})
        if not players_snapshot:
            print('No players connected, loop again')
            time.sleep(1)
            continue
        # start game
        broadcast({'type':'start'})
        time.sleep(0.5)
        # iterate questions
        for qindex in range(NUM_QUESTIONS):
            if qindex >= len(QUESTION_BANK):
                break
            q = QUESTION_BANK[qindex]
            # broadcast question
            qmsg = {'type':'question','index':qindex,'text':q['text'],'options':q['options'],'duration':QUESTION_DURATION}
            print('Broadcasting question', qindex, q['text'])
            # clear answers_queue (old answers) - simpler: drain everything currently in queue
            try:
                while True:
                    answers_queue.get_nowait()
            except queue.Empty:
                pass
            question_start = time.time()
            broadcast(qmsg)
            # wait duration while consumer collects answers
            time.sleep(QUESTION_DURATION)
            # now process answers for this question
            updates = answers_consumer(qindex, question_start, q['answer'], QUESTION_DURATION)
            # apply updates
            with clients_lock:
                for (conn,name,pts) in updates:
                    if conn in clients:
                        clients[conn]['score'] += pts
            # send updated scores to everyone
            with clients_lock:
                score_map = {entry['name']: entry['score'] for entry in clients.values()}
            broadcast({'type':'scores','scores': score_map})
            time.sleep(1)
        # game finished: compute podium
        with clients_lock:
            standings = sorted([(entry['name'], entry['score']) for entry in clients.values()], key=lambda x: -x[1])
        podium = standings[:3]
        broadcast({'type':'end','podium': podium})
        print('Game ended, podium:', podium)
        # reset scores to allow new game
        with clients_lock:
            for entry in clients.values():
                entry['score'] = 0
        # small break before potential next game
        time.sleep(3)


def accept_loop():
    global server_running
    s = socket.socket(socket.AF_INET, socket.SOCK_STREAM)
    s.setsockopt(socket.SOL_SOCKET, socket.SO_REUSEADDR, 1)
    s.bind((HOST, PORT))
    s.listen(16)
    print('Server listening on', HOST, PORT)
    threading.Thread(target=game_thread, daemon=True).start()
    try:
        while server_running:
            conn, addr = s.accept()
            t = threading.Thread(target=client_thread, args=(conn,addr), daemon=True)
            t.start()
    except KeyboardInterrupt:
        print('Server shutting down')
        server_running = False
    finally:
        s.close()

if __name__ == '__main__':
    accept_loop()


