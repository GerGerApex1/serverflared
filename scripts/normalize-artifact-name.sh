#!/usr/bin/env bash
set -euo pipefail

version="${1:?version is required}"

version="${version//:modloaders:/}"
version="${version//:spigot:/}"
version="${version//;/-}"

printf 'build-artifacts-%s\n' "$version"
