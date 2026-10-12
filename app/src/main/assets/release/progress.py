"""Read sanitized Release progress for an exact request ID. No secrets or raw logs."""
import json,pathlib,re,sys
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
                "updated_at":int(record.get("updated_at",0)),"lines":clean}
    except FileNotFoundError:
        return {"ok":True,"request_id":request,"phase":"STARTING",
                "last":"Ожидаем запуск Termux","updated_at":0,"lines":[]}
    except (OSError,ValueError,TypeError):
        return {"ok":False,"error":"UNAVAILABLE"}
if __name__=="__main__":
    print(json.dumps(read_progress(sys.argv[1] if len(sys.argv)==2 else ""),ensure_ascii=False))
