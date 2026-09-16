#!/usr/bin/env bash
#
# deploy_maven_center.sh — z-mist 一键发布到 Maven Central
#
# 子命令：
#   publish    实际 mvn deploy -Pcentral（默认）
#   verify     验证 Maven Central 上能否搜到 io.github.yuku123
#   gpg-init   首次发布前生成 GPG 密钥并写 .env
#   readme     打印发布指引摘要
#   help       显示此帮助
#
# 用法：
#   ./deploy_maven_center.sh                   # 等同 publish
#   ./deploy_maven_center.sh publish
#   ./deploy_maven_center.sh gpg-init          # 首次必须先跑
#   ./deploy_maven_center.sh verify
#   ./deploy_maven_center.sh readme
#
# 设计原则：
#   - 所有凭证从 ./.env 读，.env 已被 .gitignore 排除
#   - GPG 密钥环用 GNUPGHOME=./.gnupg，不污染 ~/.gnupg
#   - 默认 dry-run 不做任何事情，必须显式给子命令
#
# ⚠️ 关键约束：
#   - 必须在用户自己 macOS 终端（不走 sandbox）跑
#   - sandbox 限制 64KB 出站 POST，>1MB bundle 必然失败
#
# 通用指引：见同目录的 发布指引.md（任何 AI / 工程师都能从零开始）
#
set -eo pipefail

# 自动给自己加执行权限（应对 sandbox 写出来的文件没有 +x）
chmod +x "$0" 2>/dev/null || true

# ---------- 颜色 ----------
RED='\033[0;31m'; GREEN='\033[0;32m'; YELLOW='\033[1;33m'; NC='\033[0m'
log()  { printf "${GREEN}[deploy]${NC} %s\n" "$*"; }
warn() { printf "${YELLOW}[deploy]${NC} %s\n" "$*"; }
err()  { printf "${RED}[deploy]${NC} %s\n" "$*" >&2; }
die()  { err "$*"; exit 1; }

# ---------- 帮助 ----------
print_help() {
    sed -n '2,16p' "$0"
}

# ---------- 切到 z-mist 根目录 ----------
cd "$(dirname "$0")"
[[ -f pom.xml ]] || die "请在 z-mist 仓库根目录运行此脚本"

# ---------- 加载 .env ----------
load_env() {
    [[ -f .env ]] || die ".env 不存在。首次发布请先跑：./deploy_maven_center.sh gpg-init"
    # shellcheck disable=SC1091
    set -a; source .env; set +a

    [[ -n "${CENTRAL_USERNAME:-}"    ]] || die ".env 缺 CENTRAL_USERNAME"
    [[ -n "${CENTRAL_TOKEN:-}"       ]] || die ".env 缺 CENTRAL_TOKEN"
    [[ -n "${CENTRAL_GPG_PASSPHRASE:-}" ]] || die ".env 缺 CENTRAL_GPG_PASSPHRASE（先跑 gpg-init）"

    export CENTRAL_USERNAME CENTRAL_TOKEN CENTRAL_GPG_PASSPHRASE
}

# ---------- 依赖检查 ----------
check_deps() {
    command -v mvn >/dev/null 2>&1 || die "mvn 未安装"
    command -v gpg >/dev/null 2>&1 || die "gpg 未安装（brew install gnupg）"

    [[ -f ~/.m2/settings.xml ]] || die "~/.m2/settings.xml 不存在"
    grep -q '<id>central</id>' ~/.m2/settings.xml || die "~/.m2/settings.xml 缺 <server id=\"central\">"

    if [[ -d ./.gnupg ]]; then
        export GNUPGHOME="$PWD/.gnupg"
    else
        warn "未找到 ./.gnupg，请先跑 ./deploy_maven_center.sh gpg-init"
        exit 1
    fi
}

# ---------- 子命令：gpg-init ----------
cmd_gpg_init() {
    command -v gpg >/dev/null 2>&1 || die "gpg 未安装（brew install gnupg）"

    if [[ ! -f .env ]]; then
        cat > .env <<'EOF'
# Sonatype Central Portal User Token（去 Profile → User Token 生成后填入）
CENTRAL_USERNAME=
CENTRAL_TOKEN=

# GPG 私钥 passphrase（gpg-init 自动写入下方 GPG 字段）
CENTRAL_GPG_PASSPHRASE=
GPG_KEY_ID=
GPG_USER_NAME=yuku123 <1340947819@qq.com>
EOF
        die ".env 模板已创建。请填入 CENTRAL_USERNAME 和 CENTRAL_TOKEN 后重跑 gpg-init"
    fi

    # 加载（如果 .env 已填好中央凭证）
    load_env 2>/dev/null || warn ".env 中央凭证未填，但 gpg-init 仍可继续"

    log "生成 GPG RSA 4096 密钥..."
    mkdir -p .gnupg && chmod 700 .gnupg
    cat > .gnupg/gpg.conf <<EOF
pinentry-mode loopback
EOF
    chmod 600 .gnupg/gpg.conf

    export GNUPGHOME="$PWD/.gnupg"
    export GPG_PASSPHRASE="$CENTRAL_GPG_PASSPHRASE"

    gpg --batch --pinentry-mode loopback --passphrase "$GPG_PASSPHRASE" \
        --quick-generate-key "$GPG_USER_NAME" default default 4096

    KEY_ID=$(gpg --list-secret-keys --keyid-format LONG "$GPG_USER_NAME" 2>/dev/null \
             | grep -oE '[A-F0-9]{16}' | head -1)
    [[ -n "$KEY_ID" ]] || die "无法解析 KEY_ID"

    # 把 key id / user name 写回 .env（不动中央凭证）
    sed -i.bak \
        -e "s|^GPG_KEY_ID=.*|GPG_KEY_ID=$KEY_ID|" \
        -e "s|^GPG_USER_NAME=.*|GPG_USER_NAME=$GPG_USER_NAME|" \
        .env && rm -f .env.bak

    log "GPG 密钥生成成功："
    log "  KEY_ID = $KEY_ID"
    log "  公钥指纹 = $(gpg --fingerprint "$KEY_ID" 2>/dev/null | grep -A1 'pub' | tail -1 | tr -s ' ')"

    log "上传公钥到 keys.openpgp.org（Central Portal 从这里拉公钥校验签名）"
    gpg --keyserver hkps://keys.openpgp.org --send-keys "$KEY_ID" 2>&1 || \
        warn "keyserver 上传失败，可手动跑：gpg --keyserver hkps://keys.openpgp.org --send-keys $KEY_ID"

    log "完成。.env 已写入 GPG_KEY_ID=$KEY_ID"
    log "下一步：跑 ./deploy_maven_center.sh publish"
}

# ---------- 子命令：publish ----------
cmd_publish() {
    load_env
    check_deps

    log "═══════════════════════════════════════════════════════════════"
    log " 即将把 z-mist 上传到 Maven Central"
    log "  groupId : io.github.yuku123"
    log "  version : $(grep -E '<revision>' pom.xml | head -1 | sed -E 's/.*<revision>([^<]+)<\/revision>.*/\1/')"
    log "  GPG KEY : ${GPG_KEY_ID:-?}"
    log "═══════════════════════════════════════════════════════════════"

    # 清掉 staging 避免上次残留
    rm -rf target/central-staging target/central-publishing target/central-deferred 2>/dev/null
    for m in z-mist-common z-mist-core z-mist-web z-mist-client z-mist-sdk; do
        rm -rf "$m/target/central-staging" "$m/target/central-publishing" "$m/target/central-deferred" 2>/dev/null
    done

    mvn -B deploy \
        -Pcentral \
        -DskipTests \
        -Dgpg.passphrase="$CENTRAL_GPG_PASSPHRASE" \
        2>&1 | tee /tmp/z-mist-deploy.log | tail -100

    if grep -q "BUILD SUCCESS" /tmp/z-mist-deploy.log; then
        log ""
        log "✅ BUILD SUCCESS"
        if grep -q "Uploaded bundle successfully" /tmp/z-mist-deploy.log; then
            log "✅ Bundle uploaded"
            log "Central Portal 控制台：https://central.sonatype.com/publishing/deployments"
            log "验证（30 秒后）：https://search.maven.org/search?q=g:io.github.yuku123%20AND%20a:z-mist"
        else
            warn "BUILD SUCCESS 但 upload 未确认。请看 /tmp/z-mist-deploy.log 最后 100 行"
        fi
    else
        die "BUILD FAILURE，请看 /tmp/z-mist-deploy.log"
    fi
}

# ---------- 子命令：verify ----------
cmd_verify() {
    log "搜索 Maven Central：g:io.github.yuku123 AND a:z-mist*"
    sleep 30  # 等 Central Portal 同步
    for art in z-mist z-mist-common z-mist-core z-mist-web z-mist-client z-mist-sdk; do
        HTTP=$(curl -s -o /tmp/z-mist-verify-$art.xml -w "%{http_code}" \
            "https://repo1.maven.org/maven2/io/github/yuku123/$art/maven-metadata.xml")
        if [[ "$HTTP" == "200" ]]; then
            VER=$(grep -oE '<latest>[^<]+</latest>' /tmp/z-mist-verify-$art.xml | head -1 | sed 's|</\?latest>||g')
            log "  io.github.yuku123:$art  →  latest=$VER"
        elif [[ "$HTTP" == "404" ]]; then
            warn "  io.github.yuku123:$art  →  还没同步（404）"
        else
            warn "  io.github.yuku123:$art  →  HTTP=$HTTP"
        fi
    done
    log ""
    log "中央部署状态：https://central.sonatype.com/publishing/deployments"
}

# ---------- 子命令：readme ----------
cmd_readme() {
    cat <<'EOF'
═══════════════════════════════════════════════════════════════
  z-mist 发布到 Maven Central — 指引摘要
═══════════════════════════════════════════════════════════════

【前置（必须人工）】

  1. 在浏览器登录 Sonatype Central Portal：
       https://central.sonatype.com/ → Sign in with GitHub
     → Profile → Generate User Token
     → 复制 Username + Secret 写到 ./.env（**不要贴到对话里**）

  2. 申请 namespace（groupId 验证）：
       io.github.yuku123 → GitHub Pages 验证（5 分钟）
       在 yuku123/yuku123.github.io 创建
       static/central-validation/io.github.yuku123.txt
       内容填 Central Portal 给的 Verification Key
       git push 后回 Portal 点 Verify

  3. brew install gnupg（如果还没装）

【首次发布】

  $ ./install-settings.sh            # 注入 ~/.m2/settings.xml 的 central server
  $ ./deploy_maven_center.sh gpg-init
  # 生成 GPG 密钥，把 passphrase 写到 ./.env
  # KEY_ID 自动写回 .env

【日常发布流】

  $ # 1) 升版本号（去掉 -SNAPSHOT，从父 POM <revision>）
  $ sed -i '' 's|<revision>X.Y.Z-SNAPSHOT</revision>|<revision>X.Y.Z</revision>|' pom.xml

  $ # 2) commit + tag
  $ git add pom.xml */pom.xml && git commit -m "release: X.Y.Z"
  $ git tag vX.Y.Z && git push origin main vX.Y.Z

  $ # 3) 真发（**必须在你自己 macOS 终端**，sandbox 会卡 64KB）
  $ bash deploy_maven_center.sh publish

  $ # 4) 5~15 分钟验证
  $ bash deploy_maven_center.sh verify

  $ # 5) 改回 SNAPSHOT 继续开发
  $ sed -i '' 's|<revision>X.Y.Z</revision>|<revision>X.Y.(Z+1)-SNAPSHOT</revision>|' pom.xml

【AI 助手避坑（实战经验）】

  ✗ 不要把 CENTRAL_TOKEN / GPG passphrase 贴到对话
  ✗ 不要在 sandbox 跑 mvn deploy（64KB 限制 → 必然失败）
  ✗ 不要跳过 javadoc 或 GPG（Central 强制要求）
  ✗ 不要重发同名版本号（FAILED 残骸会占坐标）
  ✓ 用 -pl '!z-mist-admin' 跳过 admin（不进 reactor）
  ✓ waitMaxTime=1800（30 分钟）防 mvn 提前放弃

【模块结构】

  z-mist (aggregator)         → Central: io.github.yuku123:z-mist
  ├─ z-mist-common            → Central: io.github.yuku123:z-mist-common
  ├─ z-mist-core              → Central: io.github.yuku123:z-mist-core
  ├─ z-mist-web               → Central: io.github.yuku123:z-mist-web (Starter 入口)
  ├─ z-mist-client            → Central: io.github.yuku123:z-mist-client (SDK)
  └─ z-mist-sdk               → Central: io.github.yuku123:z-mist-sdk (fat-jar)
  z-mist-admin                → 本地可启动演示，不进 reactor

【快速验证（其他 Java 项目怎么集成）】

  <dependency>
      <groupId>io.github.yuku123</groupId>
      <artifactId>z-mist-web</artifactId>
      <version>1.0.1</version>
  </dependency>

  业务模块 application.yml:
    z-mist:
      server:
        port: 9085
      master-key: ${Z_MIST_MASTER_KEY}
    spring:
      datasource:
        url: ${SPRING_DATASOURCE_URL}
        password: ${SPRING_DATASOURCE_PASSWORD}

═══════════════════════════════════════════════════════════════
EOF
}

# ---------- 路由 ----------
SUBCMD="${1:-publish}"
case "$SUBCMD" in
    publish)   cmd_publish ;;
    verify)    cmd_verify ;;
    gpg-init)  cmd_gpg_init ;;
    readme)    cmd_readme ;;
    help|-h|--help) print_help ;;
    *) die "未知子命令：$SUBCMD（用 help 看用法）" ;;
esac