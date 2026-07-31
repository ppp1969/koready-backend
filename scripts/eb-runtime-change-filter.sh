#!/usr/bin/env bash
set -euo pipefail

if [[ "$#" -lt 2 || "$#" -gt 3 ]]; then
	echo "Usage: $0 <before-commit> <after-commit> [repository]" >&2
	exit 2
fi

before="$1"
after="$2"
repository="${3:-.}"

if ! git -C "${repository}" cat-file -e "${before}^{commit}" 2>/dev/null \
	|| ! git -C "${repository}" cat-file -e "${after}^{commit}" 2>/dev/null; then
	echo true
	exit 0
fi

runtime_paths=(
	'.dockerignore'
	'.ebignore'
	'.ebextensions'
	'.platform'
	'Dockerfile'
	'build.gradle'
	'settings.gradle'
	'gradle'
	'gradlew'
	'gradlew.bat'
	'src'
	'docs/koready-backend-design/openapi.yaml'
)

if git -C "${repository}" diff --quiet \
	"${before}" "${after}" -- "${runtime_paths[@]}"; then
	echo false
else
	echo true
fi
