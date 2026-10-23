#!/usr/bin/env bash
# Double-click launcher (macOS Finder) for package.sh
cd "$(dirname "$0")" || exit 1
./package.sh
status=$?
echo
read -n 1 -s -r -p "Press any key to close..."
echo
exit $status
