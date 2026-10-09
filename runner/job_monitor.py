"""Owned-job journal and real activity signals. No command or arbitrary-path API."""
import json
import os
import pathlib
import re
import time

MAX_BYTES = 5 * 1024 * 1024
PAGE_BYTES = 6000

class Cleaner:
    """Buffer complete lines; incomplete/oversized secret fragments never reach disk."""
    def __init__(self, redact):
        self.redact = redact
        self.buffer = ''
        self.private = False
        self.continuation = False
        self.suppressed = False
    def line(self, line):
        if self.suppressed: return ''
        if re.search(r'-----BEGIN', line):
            self.private = True
            return '[PRIVATE KEY REDACTED]\n'
        if self.private:
            if re.search(r'-----END .*PRIVATE KEY', line): self.private = False
            return ''
        if self.continuation:
            if not line.strip(): self.continuation = False
            return '[SECRET CONTINUATION REDACTED]\n' if line.strip() else '\n'
        if re.search(r'(?i)(authorization|token|password|passwd|secret|api[_-]?key)\s*[:=]', line):
            # Values may continue onto following lines. Hide the whole credential block.
            self.continuation = True
            return '[CREDENTIAL BLOCK REDACTED]\n'
        if re.search(r'(?:github_pat_|gh[pousr]_)', line) or line.strip() in ('gh','ghp','gho','ghu','ghs','ghr','github','github_pat'):
            self.continuation = True
            return '[TOKEN BLOCK REDACTED]\n'
        if re.match(r'\s*(at\s+\S+\(|File "|Traceback)', line): return '[Диагностическая трассировка скрыта]\n'
        return self.redact(line).replace('\x00', '\\0')
    def feed(self, text, final=False):
        if self.suppressed: return ''
        self.buffer += text
        result = []
        while '\n' in self.buffer:
            line, self.buffer = self.buffer.split('\n', 1)
            if len(line) > 8192:
                self.suppressed = True
                self.buffer = ''
                return ''.join(result) + '[Слишком длинная строка; канал скрыт для защиты секретов]\n'
            result.append(self.line(line + '\n'))
        if len(self.buffer) > 8192:
            self.suppressed = True
            self.buffer = ''
            return ''.join(result) + '[Слишком длинная строка; канал скрыт для защиты секретов]\n'
        if final and self.buffer:
            result.append(self.line(self.buffer + '\n')); self.buffer = ''
        return ''.join(result)

class Journal:
    def __init__(self, store, redact):
        self.store, self.redact = store, redact
        self.filters = {}
        with store.transaction() as c:
            c.execute('CREATE TABLE IF NOT EXISTS events (seq INTEGER PRIMARY KEY AUTOINCREMENT, job TEXT NOT NULL, at REAL NOT NULL, channel TEXT NOT NULL, important INTEGER NOT NULL, text TEXT NOT NULL)')
            c.execute('CREATE INDEX IF NOT EXISTS events_job_seq ON events(job,seq)')
            c.execute('CREATE TABLE IF NOT EXISTS journal_meta (job TEXT PRIMARY KEY, bytes INTEGER NOT NULL DEFAULT 0, truncated INTEGER NOT NULL DEFAULT 0, migrated INTEGER NOT NULL DEFAULT 0)')
    def append_safe(self, jid, channel, text, important=False):
        if not text: return
        with self.store.transaction() as c:
            c.execute('INSERT OR IGNORE INTO journal_meta(job) VALUES(?)', (jid,))
            used = c.execute('SELECT bytes FROM journal_meta WHERE job=?', (jid,)).fetchone()[0]
            data = text.encode('utf-8')
            if used + len(data) > MAX_BYTES:
                c.execute('UPDATE journal_meta SET truncated=1 WHERE job=?', (jid,)); return
            for start in range(0,len(text),1000):
                c.execute('INSERT INTO events(job,at,channel,important,text) VALUES(?,?,?,?,?)', (jid,time.time(),channel,int(important),text[start:start+1000]))
            c.execute('UPDATE journal_meta SET bytes=bytes+? WHERE job=?', (len(data),jid))
    def write(self, jid, channel, text, important=False):
        f = self.filters.setdefault((jid,channel), Cleaner(self.redact))
        safe = f.feed(str(text), final=channel not in ('stdout','stderr'))
        self.append_safe(jid, channel, safe, important or bool(re.search(r'^> Task |^BUILD (SUCCESSFUL|FAILED)',safe,re.M)))
        return safe
    def flush(self, jid):
        for (job,channel), f in self.filters.items():
            if job == jid: self.append_safe(jid,channel,f.feed('',final=True),channel=='stderr')
    def migrate(self, job):
        jid = job['job_id']
        with self.store.transaction() as c:
            row = c.execute('SELECT migrated FROM journal_meta WHERE job=?', (jid,)).fetchone()
            if row: return
            c.execute('INSERT INTO journal_meta(job,migrated) VALUES(?,1)', (jid,))
        self.write(jid,'event','Историческое задание: сохранён только доступный журнал прежнего runner. Полный вывод ранее не записывался.\n',True)
        for ch in ('stdout','stderr'): self.write(jid,ch,job.get(ch,'')+'\n',ch=='stderr')
        self.flush(jid)
    def page(self, job, offset=0, mode='ALL'):
        self.migrate(job)
        if type(offset) is not int or offset < 0 or mode not in ('ALL','IMPORTANT'): raise ValueError('RESULT_MALFORMED')
        pieces=[]; size=0; cursor=offset
        with self.store.transaction() as c:
            rows=c.execute('SELECT seq,at,channel,important,text FROM events WHERE job=? AND seq>? ORDER BY seq LIMIT 100', (job['job_id'],offset)).fetchall()
            for seq,at,ch,important,txt in rows:
                # Individual lines are kept below callback size; long safe text is split on write.
                body='[%s] %s: %s' % (time.strftime('%H:%M:%S',time.localtime(at)),ch,txt)
                n=len(body.encode('utf-8'))
                if mode=='IMPORTANT' and not important: cursor=seq; continue
                if size+n>PAGE_BYTES and pieces: break
                pieces.append(body);size+=n;cursor=seq
            more=c.execute('SELECT 1 FROM events WHERE job=? AND seq>? LIMIT 1',(job['job_id'],cursor)).fetchone() is not None
            meta=c.execute('SELECT bytes,truncated,migrated FROM journal_meta WHERE job=?',(job['job_id'],)).fetchone()
        keys=('job_id','project_id','action','state','created_at','started_at','ended_at','exit_code','tests','message','error_code')
        metadata={k:job.get(k) for k in keys}
        metadata['message']=self.redact(metadata.get('message') or '')[:400]
        return dict(job_id=job['job_id'],offset=offset,next_offset=cursor,text=''.join(pieces),more=more,truncated=bool(meta[1]),legacy=bool(meta[2]),metadata=metadata)
    def history(self, project, offset):
        if type(offset) is not int or not 0<=offset<=100: raise ValueError('RESULT_MALFORMED')
        with self.store.transaction() as c:
            self.store.reconcile(c)
            jobs=[json.loads(x[0]) for x in c.execute('SELECT data FROM jobs WHERE project=?',(project,))]
        jobs.sort(key=lambda j:j['created_at'],reverse=True)
        keys=('job_id','project_id','action','state','created_at','started_at','ended_at','exit_code')
        return dict(items=[{k:j.get(k) for k in keys} for j in jobs[offset:offset+20]],next_offset=offset+20,more=len(jobs)>offset+20)

class Signals:
    def __init__(self, store, jid, root):
        self.store,self.jid,self.root=store,jid,pathlib.Path(root)
        self.last_check=0; self.cpu=None; self.report_stamp=None
    def tick(self, force=False):
        now=time.time()
        if not force and now-self.last_check<2: return
        self.last_check=now
        with self.store.transaction() as c: j=self.store.get(c,self.jid)
        pid=j.get('child_pid'); alive=None; ticks=None
        if pid:
            try:
                fields=pathlib.Path('/proc/%d/stat'%pid).read_text().rsplit(')',1)[1].split()
                boot=pathlib.Path('/proc/sys/kernel/random/boot_id').read_text().strip()
                alive=fields[0]!='Z' and boot+':'+fields[19]==j.get('child_identity')
                if alive:
                    ticks=int(fields[11])+int(fields[12])+int(fields[13])+int(fields[14])
                    # Direct descendants include the Gradle daemon; inaccessible indicators remain unknown.
                    for child in pathlib.Path('/proc/%d/task/%d/children'%(pid,pid)).read_text().split():
                        try:
                            f=pathlib.Path('/proc/%s/stat'%child).read_text().rsplit(')',1)[1].split()
                            if int(f[1])==pid: ticks+=int(f[11])+int(f[12])+int(f[13])+int(f[14])
                        except (OSError,ValueError,IndexError): pass
            except (OSError,ValueError,IndexError): pass
        computed=ticks is not None and self.cpu is not None and ticks>self.cpu
        self.cpu=ticks
        stamp=None
        if j['action']=='TEST':
            directory=self.root/'app/build/test-results/testDebugUnitTest'
            try:
                if directory.resolve()==directory.absolute():
                    files=list(directory.glob('TEST-*.xml'))[:501]
                    if len(files)<=500: stamp=tuple((p.name,p.stat().st_size,p.stat().st_mtime_ns) for p in sorted(files) if not p.is_symlink())
            except OSError: pass
        changed=stamp is not None and self.report_stamp is not None and stamp!=self.report_stamp
        self.report_stamp=stamp
        updates=dict(observed_at=now,process_alive=alive,cpu_available=ticks is not None,computing=computed)
        if computed or changed: updates.update(last_progress_at=now,progress_signal='CPU' if computed else 'REPORTS')
        self.store.update(self.jid,**updates)
    def output(self, text):
        if str(text).strip():
            updates=dict(last_progress_at=time.time(),progress_signal='OUTPUT')
            tasks=re.findall(r'^> Task (.+)$',str(text),re.M)
            if tasks: updates['stage']='Gradle: '+tasks[-1][:180]
            self.store.update(self.jid,**updates)

# Split complete sanitized lines so each callback page remains strictly bounded.
def bounded_write(journal, jid, channel, text, important=False):
    f=journal.filters.setdefault((jid,channel),Cleaner(journal.redact))
    safe=f.feed(str(text),final=channel not in ('stdout','stderr'))
    while safe:
        # Unicode chars <=4 bytes; 1000 chars leaves headroom for JSON escaping.
        piece,safe=safe[:1000],safe[1000:]
        journal.append_safe(jid,channel,piece,important)
