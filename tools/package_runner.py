import hashlib
import pathlib
root=pathlib.Path(__file__).resolve().parent.parent
source=root/'runner/animadev_runner.py'
asset=root/'app/src/main/assets/runner/animadev_runner.py'
asset.parent.mkdir(parents=True,exist_ok=True)
data=source.read_bytes()
asset.write_bytes(data)
(asset.parent/'runner.sha256').write_text(hashlib.sha256(data).hexdigest()+'\n')
print('runner bytes:',len(data),'sha256:',hashlib.sha256(data).hexdigest())
