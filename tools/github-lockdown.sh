#!/usr/bin/env bash
# Run once after the repo is public. Needs: gh auth login (repo admin).
set -euo pipefail
O=SenDeliver; R=create-octo-arms; REPO="$O/$R"

echo "== repo settings"
gh api -X PATCH "repos/$REPO" -f allow_merge_commit=false -f allow_rebase_merge=false \
  -f allow_squash_merge=true -f delete_branch_on_merge=true -f allow_update_branch=true \
  -f web_commit_signoff_required=false >/dev/null

echo "== actions: read-only token, no fork secrets, approve all outside PRs"
gh api -X PUT "repos/$REPO/actions/permissions/workflow" \
  -f default_workflow_permissions=read -F can_approve_pull_request_reviews=false >/dev/null
gh api -X PUT "repos/$REPO/actions/permissions/fork-pr-contributor-approval" -f approval_policy=all_external_contributors >/dev/null || true

echo "== private vulnerability reporting"
gh api -X PUT "repos/$REPO/private-vulnerability-reporting" >/dev/null

echo "== secret scanning + push protection"
gh api -X PATCH "repos/$REPO" --input - >/dev/null <<'JSON'
{"security_and_analysis": {"secret_scanning": {"status": "enabled"}, "secret_scanning_push_protection": {"status": "enabled"}}}
JSON

echo "== ruleset: main"
gh api -X POST "repos/$REPO/rulesets" --input - >/dev/null <<'JSON'
{
  "name": "main: PR only, owner merges",
  "target": "branch",
  "enforcement": "active",
  "bypass_actors": [{"actor_id": 5, "actor_type": "RepositoryRole", "bypass_mode": "always"}],
  "conditions": {"ref_name": {"include": ["~DEFAULT_BRANCH"], "exclude": []}},
  "rules": [
    {"type": "deletion"},
    {"type": "non_fast_forward"},
    {"type": "pull_request", "parameters": {
      "required_approving_review_count": 1,
      "dismiss_stale_reviews_on_push": true,
      "require_code_owner_review": true,
      "require_last_push_approval": true,
      "required_review_thread_resolution": true,
      "allowed_merge_methods": ["squash"]
    }}
  ]
}
JSON

echo "== ruleset: release tags"
gh api -X POST "repos/$REPO/rulesets" --input - >/dev/null <<'JSON'
{
  "name": "tags: owner only",
  "target": "tag",
  "enforcement": "active",
  "bypass_actors": [{"actor_id": 5, "actor_type": "RepositoryRole", "bypass_mode": "always"}],
  "conditions": {"ref_name": {"include": ["refs/tags/v*"], "exclude": []}},
  "rules": [{"type": "creation"}, {"type": "update"}, {"type": "deletion"}, {"type": "non_fast_forward"}]
}
JSON

echo "== verify"
gh api "repos/$REPO/rulesets" -q '.[] | "\(.name) [\(.enforcement)]"'
gh api "repos/$REPO/collaborators" -q '.[].login'
