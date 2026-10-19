"""Read sanitized Release progress for an exact request ID. No secrets or raw logs."""
import json,pathlib,re,sys,time,os,subprocess

def _memory(text):
    """Linux memory counters are KiB; publish bounded MiB only."""
    rows={}
    for line in text.splitlines():
        key,sep,rest=line.partition(":")
        if sep:
            parts=rest.split()
            if parts and parts[0].isdigit():rows[key]=int(parts[0])
    total=rows.get("MemTotal",0)//1024
    available=rows.get("MemAvailable",-1)//1024
    if not 0<=available<=total<=1048576 or total==0:return {}
    result={"mem_total_mb":total,"mem_available_mb":available,
            "mem_used_mb":total-available}
    if "SwapTotal" in rows and "SwapFree" in rows:
        used=(rows["SwapTotal"]-rows["SwapFree"])//1024
        if 0<=used<=1048576:result["swap_used_mb"]=used
    return result

def _process_ticks(line):
    # /proc/PID/stat has a parenthesized comm field that may contain spaces.
    fields=line.rsplit(") ",1)[1].split()
    return int(fields[11])+int(fields[12])

def _proc_ticks():
    values=pathlib.Path("/proc/stat").read_text().splitlines()[0].split()
    if not values or values[0]!="cpu":raise ValueError("cpu")
    total=sum(int(v) for v in values[1:])
    processes=0
    ticks=0
    uid=os.getuid()
    for entry in os.scandir("/proc"):
        if not entry.name.isdecimal():continue
        try:
            if entry.stat(follow_symlinks=False).st_uid!=uid:continue
            ticks+=_process_ticks(pathlib.Path(entry.path,"stat").read_text())
            processes+=1
        except (OSError,ValueError,IndexError):
            continue  # short-lived process, or invisible to this Termux UID
    return total,ticks,processes

def _cpu_metrics(before,after,cores):
    total=after[0]-before[0]
    active=max(0,after[1]-before[1])
    if total<=0 or before[2]==0 or after[2]==0:return {}
    return {"cpu_percent":min(100,round(100*active/total)),
            "cpu_process_percent":min(100000,round(100*cores*active/total)),
            "cpu_cores":cores}

def _top_cpu():
    r=subprocess.run(["top","-b","-n","1"],capture_output=True,text=True,timeout=2,check=False)
    if r.returncode:return {}
    lines=r.stdout.splitlines()
    frames=[i for i,x in enumerate(lines) if x.strip().startswith("PID USER")]
    if not frames:return {}
    values=[]
    for line in lines[frames[-1]+1:]:
        col=line.split()
        if len(col)>8 and col[0].isdigit():
            try:
                v=float(col[8])
                if 0<=v<=10000:values.append(v)
            except ValueError:pass
    if not values:return {}
    total=round(sum(values))
    cores=max(1,os.cpu_count() or 1)
    return {"cpu_percent":min(100,round(total/cores)),
            "cpu_process_percent":min(100000,total),"cpu_cores":cores}

def resource_metrics():
    """Prefer procfs; use one top snapshot if Android restricts it."""
    result={"sampled_at":int(time.time()*1000)}
    try:result.update(_memory(pathlib.Path("/proc/meminfo").read_text()))
    except (OSError,ValueError):pass
    try:
        first=_proc_ticks()
        time.sleep(0.15)
        second=_proc_ticks()
        result.update(_cpu_metrics(first,second,max(1,os.cpu_count() or 1)))
    except (OSError,ValueError,IndexError):pass
    if "cpu_percent" not in result:
        try:result.update(_top_cpu())
        except (OSError,ValueError,subprocess.TimeoutExpired):pass
    return result

STATE=pathlib.Path("/data/data/com.termux/files/home/.local/share/animadev")
RID=re.compile(r"^[a-f0-9]{32}$")
PHASES={"STARTING","GIT","GRADLE","SIGNING","EXPORTING","DONE","FAILED"}
def read_progress(request):
    if not RID.fullmatch(request):return {"ok":False,"error":"INVALID_ID"}
    target=STATE/("release-progress-"+request+".json")
    if target.is_symlink():return {"ok":False,"error":"UNAVAILABLE"}
    try:
        raw=target.read_bytes()
        if len(raw)>20000:return {"ok":False,"error":"TOO_LARGE"}
        record=json.loads(raw)
        if record.get("request_id")!=request or record.get("phase") not in PHASES:
            return {"ok":False,"error":"INVALID_STATE"}
        lines=record.get("lines",[])
        if not isinstance(lines,list):return {"ok":False,"error":"INVALID_STATE"}
        clean=[str(x)[:165] for x in lines[-55:] if type(x) is str]
        return {"ok":True,"request_id":request,"phase":record["phase"],
                "last":str(record.get("last",""))[:165],
                "updated_at":int(record.get("updated_at",0)),"lines":clean,
                "metrics":resource_metrics()}
    except FileNotFoundError:
        return {"ok":True,"request_id":request,"phase":"STARTING",
                "last":"Ожидаем запуск Termux","updated_at":0,"lines":[],
                "metrics":resource_metrics()}
    except (OSError,ValueError,TypeError):
        return {"ok":False,"error":"UNAVAILABLE"}
if __name__=="__main__":
    print(json.dumps(read_progress(sys.argv[1] if len(sys.argv)==2 else ""),ensure_ascii=False))
