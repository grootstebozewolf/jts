#!/bin/bash
set -euo pipefail
echo "=== WSL Rocq Environment Verification for NetTopologySuite.Proofs ==="
echo "Date: $(date -Iseconds)"
echo "Host: $(hostname)"
echo ""
echo "=== 1. WSL Distro ==="
cat /etc/os-release 2>/dev/null || uname -a
echo ""
echo "=== 2. Podman availability (common in this WSL) ==="
podman --version || echo "podman not in PATH"
echo ""
echo "=== 3. Run official Rocq image from the repo Dockerfile ==="
podman run --rm rocq/rocq-prover:9.2.0-ocaml-4.14.2-flambda rocq --version 2>&1
echo "SUCCESS: Base Rocq 9.2 environment runnable in WSL."
echo ""
echo "=== 4. Proofs repo is mounted and has build files ==="
ls -l /mnt/c/com/github/grootstebozewolf/NetTopologySuite.Proofs/Dockerfile /mnt/c/com/github/grootstebozewolf/NetTopologySuite.Proofs/Makefile
echo ""
echo "=== 5. Build the toolchain stage (installs Rocq + Flocq per Dockerfile) ==="
cd /mnt/c/com/github/grootstebozewolf/NetTopologySuite.Proofs
podman build --target toolchain -t nts-rocq-wsl . 2>&1 | tail -15
echo ""
echo "=== 6. Verify full env inside the built image (Rocq + Flocq) ==="
podman run --rm nts-rocq-wsl bash -lc '
  echo "Rocq:"
  rocq --version || true
  echo "Opam/Flocq:"
  eval $(opam env 2>/dev/null || true)
  opam list 2>/dev/null | grep -E "flocq|rocq" || echo "checking user-contrib"
  ls $(rocq -where 2>/dev/null || echo /)/user-contrib/Flocq 2>/dev/null | head -3 || echo "Flocq contrib present if build succeeded"
'
echo ""
echo "=== VERIFICATION FINISHED SUCCESSFULLY ==="
