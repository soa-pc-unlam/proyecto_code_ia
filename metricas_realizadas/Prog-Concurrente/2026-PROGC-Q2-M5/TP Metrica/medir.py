"""Mide CPU y memoria (RSS) del proceso Java con psutil, muestreando cada 100 ms.
CPU: modo 100% = 1 núcleo (puede superar 100% con varios núcleos). Memoria: RSS pico en MB."""
import subprocess, psutil, time, statistics, json, sys

# Uso: python3 medir.py <carpeta_con_clase> <salida.json> [flags extra para el programa...]
DIR, SALIDA, EXTRA = sys.argv[1], sys.argv[2], sys.argv[3:]

ACCIONES = {
  "Simulación estándar (4 workers, 50 cuentas, 8M transf.)":
      ["--transferencias","8000000","--workers","4","--cuentas","50","--quiet"],
  "Estrés alta contención (16 workers, 2 cuentas, 8M transf.)":
      ["--transferencias","8000000","--workers","16","--productores","4","--cuentas","2","--quiet"],
}
REPS = 5

def correr(args):
    p = subprocess.Popen(["java","-cp",DIR,"BancoConcurrente"]+args+EXTRA, stdout=subprocess.DEVNULL, stderr=subprocess.DEVNULL)
    ps = psutil.Process(p.pid)
    ps.cpu_percent(None)
    cpu, rss = [], []
    t0 = time.time()
    while p.poll() is None:
        time.sleep(0.1)
        try:
            cpu.append(ps.cpu_percent(None)); rss.append(ps.memory_info().rss/2**20)
        except psutil.NoSuchProcess: break
    # CPU promedio global = tiempo de CPU total / tiempo de pared
    return statistics.mean(cpu), max(rss), time.time()-t0, p.returncode

res = {"cpus_logicas": psutil.cpu_count(logical=True), "mem_total_mb": round(psutil.virtual_memory().total/2**20), "acciones": {}}
for nombre,args in ACCIONES.items():
    runs=[correr(args) for _ in range(REPS)]
    assert all(r[3]==0 for r in runs), "una corrida falló"
    res["acciones"][nombre]={
      "cpu_prom": round(statistics.mean(r[0] for r in runs),1),
      "cpu_sd": round(statistics.stdev(r[0] for r in runs),1),
      "mem_pico_prom": round(statistics.mean(r[1] for r in runs),1),
      "mem_sd": round(statistics.stdev(r[1] for r in runs),1),
      "t_prom": round(statistics.mean(r[2] for r in runs),2),
      "runs":[[round(x,1) if isinstance(x,float) else x for x in r] for r in runs]}
json.dump(res, open(SALIDA,"w"), indent=2, ensure_ascii=False)
print(json.dumps(res, indent=2, ensure_ascii=False))
