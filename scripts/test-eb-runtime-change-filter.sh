#!/usr/bin/env bash
set -euo pipefail

repo_root="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
filter="${repo_root}/scripts/eb-runtime-change-filter.sh"
fixture="$(mktemp -d)"
trap 'rm -rf "${fixture}"' EXIT

git -C "${fixture}" init --quiet
git -C "${fixture}" config user.name "KoReady CI"
git -C "${fixture}" config user.email "ci@koready.invalid"
printf '# fixture\n' > "${fixture}/README.md"
git -C "${fixture}" add README.md
git -C "${fixture}" commit --quiet -m "initial"

assert_change() {
	local expected="$1"
	local path="$2"
	local value="$3"
	local before
	local after
	before="$(git -C "${fixture}" rev-parse HEAD)"
	mkdir -p "$(dirname "${fixture}/${path}")"
	printf '%s\n' "${value}" > "${fixture}/${path}"
	git -C "${fixture}" add "${path}"
	git -C "${fixture}" commit --quiet -m "change ${path}"
	after="$(git -C "${fixture}" rev-parse HEAD)"

	local actual
	actual="$("${filter}" "${before}" "${after}" "${fixture}")"
	if [[ "${actual}" != "${expected}" ]]; then
		echo "Expected ${expected} for ${path}, got ${actual}."
		exit 1
	fi
}

assert_change false docs/operations.md "docs only"
assert_change false infra/aws/template.yaml "infra only"
assert_change true src/main/java/example/App.java "runtime"
assert_change true Dockerfile "FROM scratch"
assert_change true docs/koready-backend-design/openapi.yaml "openapi: 3.0.3"

echo "EB runtime change filter tests passed."
