import hashlib
import pathlib
import re
root=pathlib.Path(__file__).resolve().parent.parent
source=root/'runner/animadev_runner.py'
asset=root/'app/src/main/assets/runner/animadev_runner.py'
asset.parent.mkdir(parents=True,exist_ok=True)
helper=root/'runner/gradle_tests.py'
helper_data=helper.read_bytes()
helper_sha=hashlib.sha256(helper_data).hexdigest()
monitor_data=(root/'runner/job_monitor.py').read_bytes()
monitor_sha=hashlib.sha256(monitor_data).hexdigest()
(asset.parent/'job_monitor.py').write_bytes(monitor_data)
(asset.parent/'monitor.sha256').write_text(monitor_sha+'\n')
text=re.sub(r"GRADLE_SHA = '[^']*'", "GRADLE_SHA = '"+helper_sha+"'", source.read_text())
text=re.sub(r"MONITOR_SHA = '[^']*'", "MONITOR_SHA = '"+monitor_sha+"'", text)
source.write_text(text)
(asset.parent/'gradle_tests.py').write_bytes(helper_data)
(asset.parent/'gradle.sha256').write_text(helper_sha+'\n')
data=source.read_bytes()
asset.write_bytes(data)
(asset.parent/'runner.sha256').write_text(hashlib.sha256(data).hexdigest()+'\n')
print('runner bytes:',len(data),'sha256:',hashlib.sha256(data).hexdigest())
