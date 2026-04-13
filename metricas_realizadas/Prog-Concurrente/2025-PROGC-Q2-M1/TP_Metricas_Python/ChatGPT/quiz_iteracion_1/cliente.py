
# ----------------- client.py -----------------
# Run: python client.py
# Make sure to start server.py first.

import socket
import threading
import json
import tkinter as tk
from tkinter import ttk, messagebox
import time

SERVER_HOST = '127.0.0.1'
SERVER_PORT = 50007

class QuizClient:
    def __init__(self, root):
        self.root = root
        self.root.title('Quiz Game Client')
        self.sock = None
        self.listener_thread = None
        self.name = None
        self.current_score = 0
        self.current_question_index = None
        self.question_deadline = None
        self.question_duration = None
        self.choice_var = tk.IntVar(value=-1)

        self.build_gui()

    def build_gui(self):
        # frames
        self.frame_entry = ttk.Frame(self.root, padding=10)
        self.frame_wait = ttk.Frame(self.root, padding=10)
        self.frame_question = ttk.Frame(self.root, padding=10)
        self.frame_podium = ttk.Frame(self.root, padding=10)

        # entry frame
        ttk.Label(self.frame_entry, text='Alias:').grid(row=0,column=0, sticky='w')
        self.name_entry = ttk.Entry(self.frame_entry)
        self.name_entry.grid(row=0,column=1, sticky='ew')
        self.join_btn = ttk.Button(self.frame_entry, text='Participar', command=self.join_game)
        self.join_btn.grid(row=1,column=0, columnspan=2, pady=5)
        self.frame_entry.grid(row=0,column=0)

        # wait frame
        ttk.Label(self.frame_wait, text='Sala de espera').grid(row=0,column=0, sticky='w')
        self.wait_players_label = ttk.Label(self.frame_wait, text='Jugadores: 0')
        self.wait_players_label.grid(row=1,column=0, sticky='w')
        self.wait_timer_label = ttk.Label(self.frame_wait, text='Comienza en: --')
        self.wait_timer_label.grid(row=2,column=0, sticky='w')

        # question frame
        self.question_label = ttk.Label(self.frame_question, text='Pregunta', wraplength=400)
        self.question_label.grid(row=0,column=0, columnspan=2)
        self.option_buttons = []
        for i in range(4):
            b = ttk.Radiobutton(self.frame_question, text=f'Opción {i+1}', variable=self.choice_var, value=i)
            b.grid(row=1+i//2, column=i%2, sticky='w', pady=2)
            self.option_buttons.append(b)
        self.time_label = ttk.Label(self.frame_question, text='Tiempo: --')
        self.time_label.grid(row=3, column=0, sticky='w')
        self.score_label = ttk.Label(self.frame_question, text='Puntos: 0')
        self.score_label.grid(row=3, column=1, sticky='e')
        self.send_btn = ttk.Button(self.frame_question, text='Enviar', command=self.send_answer)
        self.send_btn.grid(row=4, column=0, columnspan=2, pady=5)

        # podium frame
        ttk.Label(self.frame_podium, text='Podio - Top 3').grid(row=0,column=0)
        self.podium_text = tk.Text(self.frame_podium, width=40, height=10, state='disabled')
        self.podium_text.grid(row=1,column=0)

    def join_game(self):
        name = self.name_entry.get().strip()
        if not name:
            messagebox.showwarning('Alias requerido','Ingrese un alias')
            return
        self.name = name
        # connect to server
        try:
            self.sock = socket.socket(socket.AF_INET, socket.SOCK_STREAM)
            self.sock.connect((SERVER_HOST, SERVER_PORT))
        except Exception as e:
            messagebox.showerror('Error', f'No se pudo conectar al servidor: {e}')
            return
        # start listener
        self.listener_thread = threading.Thread(target=self.listen_server, daemon=True)
        self.listener_thread.start()
        # send register
        send_json(self.sock, {'type':'register','name':self.name})
        # switch to wait frame
        self.frame_entry.grid_forget()
        self.frame_wait.grid(row=0,column=0)

    def listen_server(self):
        buff = ''
        self.sock.settimeout(1.0)
        try:
            while True:
                try:
                    data = self.sock.recv(4096)
                    if not data:
                        break
                    buff += data.decode('utf-8')
                    while '\n' in buff:
                        line, buff = buff.split('\n',1)
                        if not line.strip():
                            continue
                        msg = json.loads(line)
                        self.root.after(0, self.handle_msg, msg)
                except socket.timeout:
                    continue
                except Exception as e:
                    print('recv error', e)
                    break
        finally:
            try:
                self.sock.close()
            except:
                pass
            print('Disconnected from server')
            self.root.after(0, lambda: messagebox.showinfo('Desconectado','Se perdió la conexión con el servidor'))

    def handle_msg(self, msg):
        t = msg.get('type')
        if t == 'wait':
            players = msg.get('players')
            maxp = msg.get('max')
            sec = msg.get('wait_seconds')
            self.wait_players_label.config(text=f'Jugadores: {players}/{maxp}')
            self.wait_timer_label.config(text=f'Comienza en: {sec}s')
        elif t == 'start':
            # switch to question frame (first question will arrive soon)
            self.frame_wait.grid_forget()
            self.frame_question.grid(row=0,column=0)
        elif t == 'question':
            self.current_question_index = msg.get('index')
            text = msg.get('text')
            options = msg.get('options')
            duration = int(msg.get('duration'))
            self.question_duration = duration
            self.question_deadline = time.time() + duration
            self.choice_var.set(-1)
            self.question_label.config(text=text)
            for i,opt in enumerate(options):
                self.option_buttons[i].config(text=opt, state='normal')
            self.send_btn.config(state='normal')
            self.update_countdown()
        elif t == 'scores':
            scores = msg.get('scores')
            if self.name in scores:
                self.current_score = scores[self.name]
                self.score_label.config(text=f'Puntos: {self.current_score}')
        elif t == 'end':
            podium = msg.get('podium')
            self.frame_question.grid_forget()
            self.frame_podium.grid(row=0,column=0)
            self.podium_text.config(state='normal')
            self.podium_text.delete('1.0', tk.END)
            for idx,entry in enumerate(podium, start=1):
                self.podium_text.insert(tk.END, f'{idx}. {entry[0]} - {entry[1]} puntos\n')
            self.podium_text.config(state='disabled')
        elif t == 'info':
            messagebox.showinfo('Info', msg.get('msg'))

    def update_countdown(self):
        if not self.question_deadline:
            return
        rem = int(max(0, self.question_deadline - time.time()))
        self.time_label.config(text=f'Tiempo: {rem}s')
        if rem <= 0:
            # time expired - disable
            for b in self.option_buttons:
                b.config(state='disabled')
            self.send_btn.config(state='disabled')
            return
        self.root.after(200, self.update_countdown)

    def send_answer(self):
        if self.current_question_index is None:
            return
        choice = self.choice_var.get()
        if choice < 0:
            messagebox.showwarning('Seleccionar','Seleccione una opción antes de enviar')
            return
        remaining = max(0, int(self.question_deadline - time.time()))
        msg = {'type':'answer','question':self.current_question_index,'choice':choice,'remaining': remaining}
        try:
            send_json(self.sock, msg)
        except Exception as e:
            messagebox.showerror('Error','No se pudo enviar la respuesta')
        # disable buttons after sending
        for b in self.option_buttons:
            b.config(state='disabled')
        self.send_btn.config(state='disabled')


def send_json(sock, obj):
    try:
        data = json.dumps(obj) + '\n'
        sock.sendall(data.encode('utf-8'))
    except Exception as e:
        print('send error', e)

if __name__ == '__main__':
    root = tk.Tk()
    app = QuizClient(root)
    root.mainloop()
