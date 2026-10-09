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
text=re.sub(r"GRADLE_SHA = '[^']*'", "GRADLE_SHA = '"+helper_sha+"'", source.read_text())
source.write_text(text)
(asset.parent/'gradle_tests.py').write_bytes(helper_data)
(asset.parent/'gradle.sha256').write_text(helper_sha+'\n')
data=source.read_bytes()
asset.write_bytes(data)
(asset.parent/'runner.sha256').write_text(hashlib.sha256(data).hexdigest()+'\n')
print('runner bytes:',len(data),'sha256:',hashlib.sha256(data).hexdigest())
