#!/usr/bin/env bash
#
# install-settings.sh — 注入 ~/.m2/settings.xml 的 central server 配置
#
# 作用: 让 mvn deploy -Pcentral 时携带 CENTRAL_USERNAME / CENTRAL_TOKEN
#       Central Portal User Token 替代了旧的 OSSRH 账号密码
#
# 用法:
#   ./install-settings.sh           # 合并注入（保留原有配置）
#   ./install-settings.sh --force   # 覆盖 ~/.m2/settings.xml
#
# 提示: CENTRAL_USERNAME / CENTRAL_TOKEN 从 .env 读
#
set -eo pipefail

cd "$(dirname "$0")"

# ---------- 颜色 ----------
GREEN='\033[0;32m'; YELLOW='\033[1;33m'; RED='\033[0;31m'; NC='\033[0m'
log()  { printf "${GREEN}[install]${NC} %s\n" "$*"; }
warn() { printf "${YELLOW}[install]${NC} %s\n" "$*"; }
err()  { printf "${RED}[install]${NC} %s\n" "$*" >&2; }
die()  { err "$*"; exit 1; }

FORCE=0
[[ "${1:-}" == "--force" ]] && FORCE=1

# ---------- 读取凭证 ----------
[[ -f .env ]] || die ".env 不存在。请先跑 ./deploy_maven_center.sh gpg-init 创建模板"
# shellcheck disable=SC1091
set -a; source .env; set +a

[[ -n "${CENTRAL_USERNAME:-}" ]] || die ".env 缺 CENTRAL_USERNAME"
[[ -n "${CENTRAL_TOKEN:-}"    ]] || die ".env 缺 CENTRAL_TOKEN"

# ---------- 备份 ----------
SETTINGS=~/.m2/settings.xml
if [[ -f "$SETTINGS" && $FORCE -eq 0 ]]; then
    cp "$SETTINGS" "$SETTINGS.bak.$(date +%Y%m%d-%H%M%S)"
    log "已备份原 $SETTINGS → $SETTINGS.bak.*"
fi

mkdir -p ~/.m2

# ---------- 生成 / 合并 ----------
SERVER_ID="central"
SERVER_BLOCK="<server>
    <id>$SERVER_ID</id>
    <username>${CENTRAL_USERNAME}</username>
    <password>${CENTRAL_TOKEN}</password>
  </server>"

if [[ ! -f "$SETTINGS" || $FORCE -eq 1 ]]; then
    cat > "$SETTINGS" <<EOF
<?xml version="1.0" encoding="UTF-8"?>
<settings xmlns="http://maven.apache.org/SETTINGS/1.0.0">
  <servers>
    $SERVER_BLOCK
  </servers>
</settings>
EOF
    log "已生成 $SETTINGS（包含 <server id=\"$SERVER_ID\">）"
else
    # 合并模式：用 python 替换或插入 <server id="central">...</server>
    python3 <<PYEOF
import re, sys
from pathlib import Path
p = Path("$SETTINGS")
text = p.read_text()
new_block = """$SERVER_BLOCK"""
# 移除已存在的同 id 块
text = re.sub(r"<server>\s*<id>$SERVER_ID</id>.*?</server>", "", text, flags=re.DOTALL)
# 在 </servers> 前插入
if "</servers>" in text:
    text = text.replace("</servers>", new_block + "\n  </servers>")
else:
    # 没有 <servers> 块，包一层
    text = text.replace("</settings>", "  <servers>\n    " + new_block + "\n  </servers>\n</settings>")
p.write_text(text)
print("[install] 已合并 <server id=\"$SERVER_ID\"> 到 $SETTINGS")
PYEOF
fi

log "✅ 完成。下一步：./deploy_maven_center.sh publish"