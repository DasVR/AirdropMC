# Repository And Release Workflow

## 1) Create The GitHub Repository

1. Create a new GitHub repository (recommended name: `airdrop-plugin`).
2. Keep it empty (no auto-generated README/gitignore/license) if pushing this existing project.
3. Copy remote URL, for example:
   - HTTPS: `https://github.com/<org-or-user>/airdrop-plugin.git`

## 2) Prepare Local Repository

From project root:

```powershell
git init
git branch -M main
git add .
git commit -m "chore: initialize AirdropSystem plugin project"
git remote add origin https://github.com/<org-or-user>/airdrop-plugin.git
git push -u origin main
```

## 3) Recommended Branch Strategy

- `main`: stable release-ready branch
- `develop` (optional): integration branch for ongoing work
- feature branches: `feature/<short-topic>`
- hotfix branches: `hotfix/<short-topic>`

## 4) Commit Message Style

Use concise, purpose-first commits:

- `docs: add portfolio-ready project overview and usage guides`
- `docs: add admin command matrix and operations playbook`
- `docs: add developer architecture and config reference`
- `fix: correct tier naming and command examples in documentation`

## 5) Release Checklist

1. Build and verify artifact with `.\gradlew.bat clean build`.
2. Confirm docs and examples match real command/permission behavior.
3. Tag release:

```powershell
git tag -a v2.0.0 -m "AirdropSystem 2.0.0"
git push origin main --tags
```

4. Create GitHub Release and attach shaded JAR from `build/libs/`.
5. Include release notes:
   - major feature highlights
   - config-impacting changes
   - admin action requirements

## 6) Portfolio Presentation Tips

- Pin repository and add a concise project summary.
- Keep README screenshot/gif section current if you publish media.
- Link directly to `docs/PLAYER_GUIDE.md` and `docs/DEVELOPER_GUIDE.md` in portfolio writeups.
- Keep changelog-style release notes consistent across versions.
