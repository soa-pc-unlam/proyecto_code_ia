# client.py
# Cliente GUI para el juego de preguntas.
# Ejecutar: python client.py

import socket
import threading
import json
import time
import tkinter as tk
from tkinter import ttk, messagebox

SERVER_HOST = "127.0.0.1"
SERVER_PORT = 50007
DEFAULT_QUESTION_DURATION = 20

def send_json(sock, obj):
    try:
        sock.sendall((json.dumps(obj) + "\n").encode("utf-8"))
    except Exception:
        pass

class QuizClientApp:
    def __init__(self, master):
        self.master = master
        self.master.title(f"Quiz Game - Cliente ")
        self.master.geometry("600x400")  # ventana grande
        self.sock = None
        self.listener_thread = None

        self.name = None
        self.current_score = 0
        self.current_question_index = None
        self.question_deadline = None
        self.question_duration = None
        self.answered_current = False

        # Frames
        self.frame_login = ttk.Frame(master, padding=12)
        self.frame_wait = ttk.Frame(master, padding=12)
        self.frame_question = ttk.Frame(master, padding=12)
        self.frame_podium = ttk.Frame(master, padding=12)

        # --- Login frame ---
        ttk.Label(self.frame_login, text="Alias:", font=("Arial", 14)).grid(row=0, column=0, sticky="w")
        self.entry_name = ttk.Entry(self.frame_login, font=("Arial", 13))
        self.entry_name.grid(row=0, column=1, sticky="ew", padx=6)
        self.join_btn = ttk.Button(self.frame_login, text="Participar", command=self.on_join, width=20)
        self.join_btn.grid(row=1, column=0, columnspan=2, pady=10)
        self.frame_login.grid(row=0, column=0, sticky="nsew")
        self.frame_login.columnconfigure(1, weight=1)

        # --- Wait frame ---
        ttk.Label(self.frame_wait, text="Sala de espera", font=("Arial", 18)).grid(row=0, column=0, sticky="w")
        self.wait_players_label = ttk.Label(self.frame_wait, text="Jugadores: 0/0", font=("Arial", 14))
        self.wait_players_label.grid(row=1, column=0, sticky="w", pady=(6,0))
        self.wait_timer_label = ttk.Label(self.frame_wait, text="Comienza en: -- s", font=("Arial", 14))
        self.wait_timer_label.grid(row=2, column=0, sticky="w", pady=(6,0))

        # --- Question frame ---
        self.question_label = ttk.Label(self.frame_question, text="Pregunta", font=("Arial", 16), wraplength=740)
        self.question_label.grid(row=0, column=0, columnspan=2, sticky="w", pady=(0,8))
        self.choice_var = tk.IntVar(value=-1)
        self.option_rbs = []
        for i in range(4):
            rb = ttk.Radiobutton(self.frame_question, text=f"Opción {i+1}", variable=self.choice_var, value=i)
            rb.grid(row=1+i, column=0, columnspan=2, sticky="w", pady=4)
            self.option_rbs.append(rb)
        self.scores_text = tk.Text(self.frame_question, height=6, width=60, state="disabled", font=("Arial", 12))
        self.scores_text.grid(row=6, column=0, columnspan=2, pady=(8,0))
        self.time_label = ttk.Label(self.frame_question, text="Tiempo: -- s", font=("Arial", 14))
        self.time_label.grid(row=7, column=0, sticky="w", pady=(8,0))
        self.score_label = ttk.Label(self.frame_question, text="Puntos: 0", font=("Arial", 14))
        self.score_label.grid(row=7, column=1, sticky="e", pady=(8,0))
        self.send_btn = ttk.Button(self.frame_question, text="Enviar", command=self.on_send_answer, width=20)
        self.send_btn.grid(row=8, column=0, columnspan=2, pady=(12,0))

        # --- Podium frame ---
        ttk.Label(self.frame_podium, text="🏆 Podio - Top 3 🏆", font=("Arial", 20)).pack(pady=(6,12))
        self.podium_text = tk.Text(self.frame_podium, height=10, width=80, state="disabled", font=("Arial", 14))
        self.podium_text.pack()

        # set initial visible frames
        self.frame_wait.grid_forget()
        self.frame_question.grid_forget()
        self.frame_podium.grid_forget()

    def on_join(self):
        alias = self.entry_name.get().strip()
        if not alias:
            messagebox.showwarning("Alias requerido", "Ingrese un alias para participar.")
            return
        self.name = alias
        if not self.connect_to_server():
            return
        # enviar registro
        send_json(self.sock, {"type": "register", "name": self.name})
        # cambiar a sala de espera
        self.frame_login.grid_forget()
        self.frame_wait.grid(row=0, column=0, sticky="nsew")

    def connect_to_server(self):
        try:
            self.sock = socket.socket(socket.AF_INET, socket.SOCK_STREAM)
            self.sock.connect((SERVER_HOST, SERVER_PORT))
            self.listener_thread = threading.Thread(target=self.listen_server, daemon=True)
            self.listener_thread.start()
            self.master.title(f"Quiz Game - {self.name}")
            return True
        except Exception as e:
            messagebox.showerror("Error", f"No se pudo conectar al servidor: {e}")
            return False

    def listen_server(self):
        buffer = ""
        try:
            self.sock.settimeout(1.0)
            while True:
                try:
                    data = self.sock.recv(4096)
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
                        self.master.after(0, self.handle_server_msg, msg)
                except socket.timeout:
                    continue
                except Exception:
                    break
        finally:
            try:
                self.sock.close()
            except:
                pass
            self.master.after(0, lambda: messagebox.showinfo("Desconectado", "Conexión con servidor perdida."))

    def handle_server_msg(self, msg):
        mtype = msg.get("type")
        if mtype == "full":
            messagebox.showerror("Sala llena", msg.get("msg", "La sala está llena."))
            try:
                self.sock.close()
            except:
                pass
            self.master.destroy()
        elif mtype == "registered":
            # confirmación
            pass
        elif mtype == "wait":
            players = msg.get("players", 0)
            maxp = msg.get("max", "?")
            seconds = msg.get("wait_seconds", 0)
            self.wait_players_label.config(text=f"Jugadores: {players}/{maxp}")
            self.wait_timer_label.config(text=f"Comienza en: {seconds} s")
        elif mtype == "start":
            self.frame_wait.grid_forget()
            self.frame_question.grid(row=0, column=0, sticky="nsew")
        elif mtype == "question":
            qidx = int(msg.get("index", -1))
            text = msg.get("text", "")
            options = msg.get("options", [])
            duration = int(msg.get("duration", DEFAULT_QUESTION_DURATION))
            self.show_question(qidx, text, options, duration)
        elif mtype == "scores":
            scores = msg.get("scores", {})
            # actualizar own score
            if self.name in scores:
                self.current_score = scores[self.name]
                self.score_label.config(text=f"Puntos: {self.current_score}")
            # mostrar listado de puntajes parciales
            self.show_scores_list(scores)
        elif mtype == "end":
            podium = msg.get("podium", [])
            self.show_podium(podium)
        elif mtype == "info":
            messagebox.showinfo("Info", msg.get("msg", ""))
        else:
            # message type desconocido -> ignorar
            pass

    def show_question(self, index, text, options, duration):
        self.current_question_index = int(index)
        self.question_duration = int(duration)
        self.question_deadline = time.time() + self.question_duration
        self.answered_current = False
        self.choice_var.set(-1)

        # actualizar UI con la pregunta y opciones
        self.question_label.config(text=f"Pregunta {index + 1}: {text}")
        for i in range(4):
            opt_text = options[i] if i < len(options) else f"Opción {i+1}"
            self.option_rbs[i].config(text=opt_text, state="normal")
        self.send_btn.config(state="normal")
        # limpiar scores list (se actualizará cuando lleguen scores)
        self.scores_text.config(state="normal")
        self.scores_text.delete("1.0", tk.END)
        self.scores_text.insert(tk.END, "Puntajes parciales aparecerán aquí...\n")
        self.scores_text.config(state="disabled")
        # iniciar contador visual
        self.update_countdown()

    def update_countdown(self):
        if not self.question_deadline:
            return
        rem = int(max(0, self.question_deadline - time.time()))
        self.time_label.config(text=f"Tiempo: {rem} s")
        if rem <= 0:
            # tiempo expirado: deshabilitar
            for rb in self.option_rbs:
                rb.config(state="disabled")
            self.send_btn.config(state="disabled")
            self.answered_current = True
            return
        # seguir actualizando
        self.master.after(200, self.update_countdown)

    def on_send_answer(self):
        if self.current_question_index is None:
            return
        choice = self.choice_var.get()
        if choice < 0:
            messagebox.showwarning("Seleccionar", "Seleccione una opción antes de enviar.")
            return
        msg = {"type": "answer", "question": self.current_question_index, "choice": int(choice)}
        send_json(self.sock, msg)
        # deshabilitar para evitar re-envíos
        for rb in self.option_rbs:
            rb.config(state="disabled")
        self.send_btn.config(state="disabled")
        self.answered_current = True

    def show_scores_list(self, scores):
        self.scores_text.config(state="normal")
        self.scores_text.delete("1.0", tk.END)
        if not scores:
            self.scores_text.insert(tk.END, "Sin puntajes aún.\n")
        else:
            items = sorted(scores.items(), key=lambda x: -x[1])
            for name, pts in items:
                self.scores_text.insert(tk.END, f"{name}: {pts}\n")
        self.scores_text.config(state="disabled")

    def show_podium(self, podium):
        self.frame_question.grid_forget()
        self.frame_podium.grid(row=0, column=0, sticky="nsew")
        self.podium_text.config(state="normal")
        self.podium_text.delete("1.0", tk.END)
        if not podium:
            self.podium_text.insert(tk.END, "No hay jugadores.\n")
        else:
            for idx, entry in enumerate(podium, start=1):
                name = entry[0]
                pts = entry[1]
                self.podium_text.insert(tk.END, f"{idx}. {name} - {pts} puntos\n")
        self.podium_text.config(state="disabled")


def main():
    root = tk.Tk()
    app = QuizClientApp(root)
    root.mainloop()


if __name__ == "__main__":
    main()
