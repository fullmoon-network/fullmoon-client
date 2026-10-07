#!/usr/bin/env bash
# setup-runner.sh: prepares an Amazon Linux 2023 aws-burst runner (m7i-flex.large) for the bench.
# Runs ON the runner after the harness and jars were rsynced to ~/bench (see README.md).
# Installs Xvfb + Mesa llvmpipe + ImageMagick + XTEST, a venv with minecraft-launcher-lib, then
# vanilla 26.1.2, its Mojang Java 25 runtime and Fabric loader 0.19.3 from the official endpoints.
set -euo pipefail
B="$HOME/bench"
cd "$B"
sudo dnf -y -q install xorg-x11-server-Xvfb mesa-dri-drivers mesa-libGL mesa-libEGL mesa-libgbm \
  libXrandr libXcursor libXxf86vm libXi libXext libXrender libX11 libXtst xdpyinfo ImageMagick \
  python3.12 python3.12-pip >/dev/null
rm -rf venv; python3.12 -m venv venv
venv/bin/pip install -q 'minecraft-launcher-lib==8.0'
(cd jars && sha256sum -c ../jars.sha256 --quiet)
venv/bin/python - <<'PY'
import minecraft_launcher_lib as lib
from pathlib import Path
engine = str(Path.home() / '.minecraft')
callback = {'setStatus': lambda s: None, 'setProgress': lambda p: None, 'setMax': lambda m: None}
lib.install.install_minecraft_version('26.1.2', engine, callback=callback)
java = Path(engine) / 'runtime/java-runtime-epsilon/linux/java-runtime-epsilon/bin/java'
lib.fabric.install_fabric('26.1.2', engine, loader_version='0.19.3', callback=callback, java=str(java))
print('installed', [v['id'] for v in lib.utils.get_installed_versions(engine)])
PY
~/.minecraft/runtime/java-runtime-epsilon/linux/java-runtime-epsilon/bin/java -version 2>&1 | head -2
lscpu | grep -E 'Model name|^CPU\(s\)'
echo "setup-runner ok"
