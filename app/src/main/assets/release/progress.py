"""Read sanitized Release progress for an exact request ID. No secrets or raw logs."""
import json,pathlib,re,sys
import subprocess,time,os

def resource_metrics():
    """Numbers only: never return process listings, arguments, secrets or raw logs."""
    result={"sampled_at":int(time.time()*1000)}
    try:
        free=subprocess.run(["free","-m"],capture_output=True,text=True,
                            timeout=2,check=False)
        if free.returncode==0:
            memory=next((line.split() for line in free.stdout.splitlines()
                         if line.startswith("Mem:")),None)
            if memory and len(memory)>=7:
                total,available=int(memory[1]),int(memory[6])
                if 0<=available<=total<=1048576:
                    result.update(mem_total_mb=total,mem_available_mb=available,
                                  mem_used_mb=total-available)
            swap=next((line.split() for line in free.stdout.splitlines()
                       if line.startswith("Swap:")),None)
            if swap and len(swap)>=4:
                result["swap_used_mb"]=max(0,min(int(swap[2]),1048576))
    except (OSError,ValueError,subprocess.TimeoutExpired):
        pass
    try:
        top=subprocess.run(["top","-b","-n","2","-d","1"],capture_output=True,
                           text=True,timeout=4,check=False)
        if top.returncode==0:
            lines=top.stdout.splitlines()
            frames=[i for i,line in enumerate(lines) if line.strip().startswith("PID USER")]
            if frames:
                samples=[]
                for line in lines[frames[-1]+1:]:
                    cols=line.split()
                    if len(cols)>=9 and cols[0].isdigit():
                        try:
                            value=float(cols[8])
                            if 0<=value<=10000:samples.append(value)
                        except ValueError:
                            continue
                if samples:
                    cores=max(1,os.cpu_count() or 1)
                    process_total=sum(samples)
                    result["cpu_percent"]=min(100,round(process_total/cores))
                    result["cpu_process_percent"]=min(100000,round(process_total))
                    result["cpu_cores"]=cores
    except (OSError,ValueError,subprocess.TimeoutExpired):
        pass
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
