"""One-shot local request for Release Gradle Ctrl+C; only a verified 128-bit job ID."""
import json,os,pathlib,re,sys

STATE=pathlib.Path("/data/data/com.termux/files/home/.local/share/animadev")
def main():
    jid=sys.argv[1] if len(sys.argv)==2 else ""
    reply={"request_id":jid,"ok":False,"code":"JOB_NOT_ACTIVE"}
    try:
        if not re.fullmatch(r"[a-f0-9]{32}",jid):raise ValueError()
        if not STATE.is_dir() or STATE.is_symlink():raise ValueError()
        active=STATE/(".release-active-"+jid)
        cancel=STATE/(".release-cancel-"+jid)
        if active.is_file() and not active.is_symlink() and not cancel.is_symlink():
            try:
                fd=os.open(cancel,os.O_CREAT|os.O_EXCL|os.O_WRONLY|os.O_NOFOLLOW,0o600)
                os.close(fd)
            except FileExistsError:
                pass
            reply.update(ok=True,code="CANCEL_REQUESTED")
    except (OSError,ValueError):
        reply["code"]="CANCEL_UNAVAILABLE"
    print(json.dumps(reply))
if __name__=="__main__":main()
