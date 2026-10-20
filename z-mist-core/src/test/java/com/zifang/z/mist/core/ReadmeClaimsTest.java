package com.zifang.z.mist.core;

import org.junit.jupiter.api.Test;
import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.Node;
import org.w3c.dom.NodeList;

import javax.xml.parsers.DocumentBuilderFactory;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Set;
import java.util.TreeSet;
import java.util.function.Supplier;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * z-mist README 定量主张自守卫。
 *
 * <p>判据：README 里写死的定量主张（版本号 / 父项目版本 / reactor 模块数 / 端口 / 调度间隔 /
 * 控制器与调度器名单）必须能从仓内源码重算出来 —— README 改了就红，源码变了 README 没跟上也得红。
 *
 * <p>口径约定（踩过的坑，勿随意改）：
 * <ul>
 *   <li>根 pom 的 version / parent version / module 数走 XML 解析：正则会抓到 parent 里的
 *       {@code <version>}，也躲不掉被 {@code <!-- -->} 注释掉的 {@code <module>}；
 *       CI-friendly 版本（{@code ${revision}}）读 {@code <revision>} 属性；</li>
 *   <li>README 里的数字靠「定位标签 + 抠数正则」取，<b>抠不到就红</b>（不允许「没匹配上就跳过」，
 *       那正是空跑）；</li>
 *   <li>定位标签用「表格行首关键字」或「唯一行」，不拿数字本身当定位标签
 *       （短数字是长数字的子串，按数字定位会匹配到别人的行上）；</li>
 *   <li>端口 9085 取自源码常量 {@code ProtocolConstant.DEFAULT_PORT}，不是从 README 反推；</li>
 *   <li>「60s」这种带单位的口径：源码侧是毫秒（{@code @Scheduled(fixedRate = 60_000L)}），
 *       守卫统一换算成秒再比；</li>
 *   <li>名单型主张（控制器 / 调度器）：README 表格里点名的类名集合必须与源码里的类名集合
 *       <b>完全相等</b> —— 少一个（新增了类没更新 README）或多一个（README 写了不存在的类）都红；</li>
 *   <li>本仓 README 里「对齐前基线 39 项」是历史测量（带历史锚点），不纳入守卫，也不动它。</li>
 * </ul>
 */
class ReadmeClaimsTest {

    // ------------------------------------------------------------------ 定位与读取

    /** 仓根：测试跑在模块目录，user.dir 不是仓根 —— 向上找同时有 README.md 与 pom.xml 的目录（最多 5 层）。 */
    private static Path repoRoot() {
        Path dir = Paths.get(System.getProperty("user.dir")).toAbsolutePath().normalize();
        for (int i = 0; i < 5 && dir != null; i++) {
            if (Files.isRegularFile(dir.resolve("README.md")) && Files.isRegularFile(dir.resolve("pom.xml"))) {
                return dir;
            }
            dir = dir.getParent();
        }
        throw new AssertionError("定位仓根失败：从 " + System.getProperty("user.dir")
                + " 向上 5 层内没有同时含 README.md 与 pom.xml 的目录");
    }

    /** README 全文（统一成 \n 换行，便于按行算偏移）。 */
    private static String readmeText() {
        try {
            String raw = new String(Files.readAllBytes(repoRoot().resolve("README.md")), StandardCharsets.UTF_8);
            return raw.replace("\r\n", "\n");
        } catch (IOException e) {
            throw new AssertionError("读取 README.md 失败", e);
        }
    }

    // ------------------------------------------------------------------ 源码重算

    /** 根 pom 的 project 元素（XML 解析：注释掉的 <module> 天然不计入）。 */
    private static Element rootProject() {
        try {
            DocumentBuilderFactory factory = DocumentBuilderFactory.newInstance();
            factory.setNamespaceAware(false);
            factory.setExpandEntityReferences(false);
            Document doc = factory.newDocumentBuilder().parse(repoRoot().resolve("pom.xml").toFile());
            return doc.getDocumentElement();
        } catch (Exception e) {
            throw new AssertionError("解析根 pom.xml 失败", e);
        }
    }

    private static Element directChild(Element parent, String name) {
        if (parent == null) {
            return null;
        }
        NodeList children = parent.getChildNodes();
        for (int i = 0; i < children.getLength(); i++) {
            Node node = children.item(i);
            if (node.getNodeType() == Node.ELEMENT_NODE && name.equals(node.getNodeName())) {
                return (Element) node;
            }
        }
        return null;
    }

    /** 根 pom 的版本号；CI-friendly（${revision}）时读 <revision> 属性。 */
    private static String rootPomVersion() {
        Element project = rootProject();
        Element version = directChild(project, "version");
        if (version == null) {
            throw new AssertionError("根 pom.xml 没有 <version>");
        }
        String raw = version.getTextContent().trim();
        if (raw.contains("${revision}")) {
            Element properties = directChild(project, "properties");
            Element revision = properties == null ? null : directChild(properties, "revision");
            if (revision == null) {
                throw new AssertionError("根 pom.xml 用 ${revision} 但没有 <revision> 属性");
            }
            return revision.getTextContent().trim();
        }
        return raw;
    }

    /** 根 pom <parent> 的版本号（README「父项目」行）。 */
    private static String rootParentVersion() {
        Element parent = directChild(rootProject(), "parent");
        Element version = directChild(parent, "version");
        if (version == null) {
            throw new AssertionError("根 pom.xml 的 <parent> 没有 <version>");
        }
        return version.getTextContent().trim();
    }

    /** 根 pom <modules> 下实际参与构建的模块数（被注释掉的 module 不算）。 */
    private static int rootPomModuleCount() {
        Element modules = directChild(rootProject(), "modules");
        if (modules == null) {
            throw new AssertionError("根 pom.xml 没有 <modules>");
        }
        int count = 0;
        NodeList children = modules.getChildNodes();
        for (int i = 0; i < children.getLength(); i++) {
            Node node = children.item(i);
            if (node.getNodeType() == Node.ELEMENT_NODE && "module".equals(node.getNodeName())) {
                count++;
            }
        }
        return count;
    }

    /** 读一个源码文件（相对仓根）。 */
    private static String sourceText(String relativePath) {
        Path file = repoRoot().resolve(relativePath);
        if (!Files.isRegularFile(file)) {
            throw new AssertionError("源码文件不存在：" + relativePath);
        }
        try {
            return new String(Files.readAllBytes(file), StandardCharsets.UTF_8);
        } catch (IOException e) {
            throw new AssertionError("读取 " + relativePath + " 失败", e);
        }
    }

    /** 源码常量 ProtocolConstant.DEFAULT_PORT（Netty 私有协议默认端口）。 */
    private static int protocolDefaultPort() {
        String src = sourceText("z-mist-common/src/main/java/com/zifang/z/mist/common/connect/ProtocolConstant.java");
        Matcher m = Pattern.compile("DEFAULT_PORT\\s*=\\s*(\\d+)").matcher(src);
        if (!m.find()) {
            throw new AssertionError("ProtocolConstant 里找不到 DEFAULT_PORT");
        }
        return Integer.parseInt(m.group(1));
    }

    /** RotationScheduler 的扫描间隔（秒）：源码是毫秒的 fixedRate，统一换算成秒。 */
    private static int rotationScanSeconds() {
        String src = sourceText("z-mist-web/src/main/java/com/zifang/z/mist/web/scheduler/RotationScheduler.java");
        Matcher m = Pattern.compile("@Scheduled\\s*\\(\\s*fixedRate\\s*=\\s*([0-9_]+)L?\\s*\\)")
                .matcher(src);
        if (!m.find()) {
            throw new AssertionError("RotationScheduler 里找不到 @Scheduled(fixedRate = ...)");
        }
        String millis = m.group(1).replace("_", "");
        long value = Long.parseLong(millis);
        if (value % 1000L != 0L) {
            throw new AssertionError("RotationScheduler 的 fixedRate 不是整秒：" + millis);
        }
        return (int) (value / 1000L);
    }

    /** 某目录下的类名集合（简单类名，去 .java 后缀）。 */
    private static List<String> classNamesIn(String relativeDir, String suffix) {
        Path base = repoRoot().resolve(relativeDir);
        if (!Files.isDirectory(base)) {
            throw new AssertionError("源码目录不存在：" + relativeDir);
        }
        try (Stream<Path> stream = Files.walk(base)) {
            List<String> names = stream.filter(Files::isRegularFile)
                    .map(p -> p.getFileName().toString())
                    .filter(n -> n.endsWith(suffix))
                    .map(n -> n.substring(0, n.length() - ".java".length()))
                    .sorted()
                    .collect(Collectors.toList());
            if (names.isEmpty()) {
                throw new AssertionError("目录里没有匹配 " + suffix + " 的类：" + relativeDir);
            }
            return names;
        } catch (IOException e) {
            throw new AssertionError("扫描 " + relativeDir + " 失败", e);
        }
    }

    /** z-mist-web 的 REST 控制器类名集合。 */
    private static List<String> webControllerNames() {
        return classNamesIn("z-mist-web/src/main/java", "Controller.java");
    }

    /** z-mist-web 的调度器类名集合。 */
    private static List<String> schedulerNames() {
        return classNamesIn("z-mist-web/src/main/java/com/zifang/z/mist/web/scheduler", ".java");
    }

    // ------------------------------------------------------------------ README 主张抽取

    /** 主张所在文本块：全文区间 + 文本。 */
    private static final class Block {
        final int start;
        final int end;
        final String text;

        Block(int start, int end, String text) {
            this.start = start;
            this.end = end;
            this.text = text;
        }
    }

    /** 主张定位：把 README 全文映射到一个唯一的文本块。 */
    private interface Anchor {
        Block locate(String readme, String label);
    }

    /** README 侧取值：从主张块里抠出「值」（数字串，或类名集合的规范化串）。抠不到就红。 */
    private interface ValueExtractor {
        String extract(Block block, String label);
    }

    /** 合成变异：返回一份「把这条主张改错」的 README 全文。 */
    private interface Mutator {
        String mutate(String readme, Block block, String label);
    }

    /** 一条定量主张：定位标签 + 抠值 + 源码重算口径 + 变异方式。 */
    private static final class Claim {
        final String label;
        final Anchor anchor;
        final ValueExtractor extract;
        final Supplier<String> expected;
        final Mutator mutate;

        Claim(String label, Anchor anchor, ValueExtractor extract, Supplier<String> expected, Mutator mutate) {
            this.label = label;
            this.anchor = anchor;
            this.extract = extract;
            this.expected = expected;
            this.mutate = mutate;
        }
    }

    private static List<String> lines(String readme) {
        return Arrays.asList(readme.split("\n", -1));
    }

    private static int lineStart(String readme, int lineIndex) {
        int idx = 0;
        for (int i = 0; i < lineIndex; i++) {
            int nl = readme.indexOf('\n', idx);
            if (nl < 0) {
                return readme.length();
            }
            idx = nl + 1;
        }
        return idx;
    }

    /** 定位 README 中唯一匹配的行；找不到或不唯一都红。 */
    private static Block uniqueLine(String readme, Pattern anchor, String label) {
        List<String> all = lines(readme);
        List<Integer> hits = new ArrayList<>();
        for (int i = 0; i < all.size(); i++) {
            if (anchor.matcher(all.get(i)).find()) {
                hits.add(i);
            }
        }
        if (hits.isEmpty()) {
            throw new AssertionError("README 里找不到主张行：" + label + "（定位 " + anchor.pattern() + "）");
        }
        if (hits.size() > 1) {
            throw new AssertionError("README 里主张行定位不唯一（命中 " + hits.size() + " 行）：" + label);
        }
        int i = hits.get(0);
        int start = lineStart(readme, i);
        return new Block(start, start + all.get(i).length(), all.get(i));
    }

    /** 定位 README 里以表头开头的整张表格（表头行 + 后续所有以 | 开头的行）。 */
    private static Block tableBlock(String readme, Pattern header, String label) {
        List<String> all = lines(readme);
        List<Integer> hits = new ArrayList<>();
        for (int i = 0; i < all.size(); i++) {
            if (header.matcher(all.get(i)).find()) {
                hits.add(i);
            }
        }
        if (hits.isEmpty()) {
            throw new AssertionError("README 里找不到表格：" + label + "（定位 " + header.pattern() + "）");
        }
        if (hits.size() > 1) {
            throw new AssertionError("README 里表格定位不唯一（命中 " + hits.size() + " 处）：" + label);
        }
        int first = hits.get(0);
        int last = first;
        while (last + 1 < all.size() && all.get(last + 1).startsWith("|")) {
            last++;
        }
        StringBuilder sb = new StringBuilder();
        for (int i = first; i <= last; i++) {
            if (i > first) {
                sb.append('\n');
            }
            sb.append(all.get(i));
        }
        int start = lineStart(readme, first);
        int end = lineStart(readme, last) + all.get(last).length();
        return new Block(start, end, sb.toString());
    }

    /** 数字型取值：正则第 1 组。 */
    private static ValueExtractor number(final Pattern pattern) {
        return (block, label) -> {
            Matcher m = pattern.matcher(block.text);
            if (!m.find()) {
                throw new AssertionError("README 主张里抠不到数字：" + label + "（正则 " + pattern.pattern() + "）");
            }
            return m.group(1);
        };
    }

    /** 名单型取值：抠出块里反引号包住的类名，排序后拼成 "A,B,C"。 */
    private static ValueExtractor nameSet(final Pattern namePattern) {
        return (block, label) -> {
            Set<String> names = new TreeSet<>();
            Matcher m = namePattern.matcher(block.text);
            while (m.find()) {
                names.add(m.group(1));
            }
            if (names.isEmpty()) {
                throw new AssertionError("README 主张里抠不到类名：" + label + "（正则 " + namePattern.pattern() + "）");
            }
            return String.join(",", names);
        };
    }

    /** 数字型变异：把抠出的数字末段 +1。 */
    private static Mutator shiftNumber(final Pattern pattern) {
        return (readme, block, label) -> {
            Matcher m = pattern.matcher(block.text);
            if (!m.find()) {
                throw new AssertionError("变异前提不成立（抠不到数字）：" + label);
            }
            String original = m.group(1);
            String mutated = shiftLastSegment(original);
            assertNotEquals(original, mutated, "变异没有生效：" + label);
            int from = block.start + m.start(1);
            int to = block.start + m.end(1);
            return readme.substring(0, from) + mutated + readme.substring(to);
        };
    }

    /** 名单型变异：把抠到的第一个类名替换成小写占位词（集合里就少一个真类名）。 */
    private static Mutator dropFirstName(final Pattern namePattern) {
        return (readme, block, label) -> {
            Matcher m = namePattern.matcher(block.text);
            if (!m.find()) {
                throw new AssertionError("变异前提不成立（抠不到类名）：" + label);
            }
            int from = block.start + m.start(1);
            int to = block.start + m.end(1);
            return readme.substring(0, from) + "removed" + readme.substring(to);
        };
    }

    /** 数字末段自增：1.0.4 → 1.0.5；9085 → 9086；60 → 61。 */
    private static String shiftLastSegment(String raw) {
        String[] parts = raw.split("\\.");
        int last = parts.length - 1;
        parts[last] = String.valueOf(Integer.parseInt(parts[last]) + 1);
        return String.join(".", parts);
    }

    // ------------------------------------------------------------------ 主张表

    private static List<Claim> claims() {
        List<Claim> list = new ArrayList<>();

        // 1. 基本信息表：当前版本 = 根 POM <revision>
        Pattern version = Pattern.compile("`(\\d+\\.\\d+\\.\\d+)`");
        list.add(new Claim("根 POM 版本号（当前版本）",
                (readme, label) -> uniqueLine(readme, Pattern.compile("\\*\\*当前版本\\*\\*"), label),
                number(version),
                ReadmeClaimsTest::rootPomVersion,
                shiftNumber(version)));

        // 2. 基本信息表：父项目版本 = 根 POM <parent><version>
        Pattern parentVersion = Pattern.compile("z-boot-parent:(\\d+\\.\\d+\\.\\d+)");
        list.add(new Claim("父项目版本号",
                (readme, label) -> uniqueLine(readme, Pattern.compile("\\*\\*父项目\\*\\*"), label),
                number(parentVersion),
                ReadmeClaimsTest::rootParentVersion,
                shiftNumber(parentVersion)));

        // 3. 项目结构说明：reactor 实际参与构建的模块数
        Pattern moduleCount = Pattern.compile("(\\d+)\\s*个模块");
        list.add(new Claim("reactor 模块数",
                (readme, label) -> uniqueLine(readme, Pattern.compile("进入 Maven reactor"), label),
                number(moduleCount),
                () -> String.valueOf(rootPomModuleCount()),
                shiftNumber(moduleCount)));

        // 4. 基本信息表：Netty 私有协议默认端口
        Pattern basicPort = Pattern.compile("Netty 私有协议\\s*`(\\d+)`");
        list.add(new Claim("Netty 私有协议默认端口（基本信息表）",
                (readme, label) -> uniqueLine(readme, Pattern.compile("\\*\\*默认端口\\*\\*"), label),
                number(basicPort),
                () -> String.valueOf(protocolDefaultPort()),
                shiftNumber(basicPort)));

        // 5. 配置表：z-mist.server.port 默认端口（同一端口的第二处表述）
        Pattern configPort = Pattern.compile("默认\\s*(\\d+)");
        list.add(new Claim("Netty 私有协议端口（配置表）",
                (readme, label) -> uniqueLine(readme, Pattern.compile("^\\|\\s*`z-mist\\.server\\.port`"), label),
                number(configPort),
                () -> String.valueOf(protocolDefaultPort()),
                shiftNumber(configPort)));

        // 6. 能力清单：密钥轮换扫描间隔（60s）
        //    正则只认「（60s）」这个带单位的形态，不绑类名 —— 名单型主张的变异要删掉某个类名，
        //    两条主张共用同一行时，绑了类名就会一次变异弄红两条，误差就指不准了。
        Pattern scanInterval = Pattern.compile("（\\s*(\\d+)\\s*s\\s*）");
        list.add(new Claim("密钥轮换扫描间隔",
                (readme, label) -> uniqueLine(readme, Pattern.compile("^\\|\\s*定时任务\\s*\\|"), label),
                number(scanInterval),
                () -> String.valueOf(rotationScanSeconds()),
                shiftNumber(scanInterval)));

        // 7. 能力清单：定时任务点名的调度器集合 = z-mist-web/scheduler 下的类集合
        Pattern schedulerName = Pattern.compile("`([A-Z][A-Za-z0-9]*)`");
        list.add(new Claim("定时任务调度器集合",
                (readme, label) -> uniqueLine(readme, Pattern.compile("^\\|\\s*定时任务\\s*\\|"), label),
                nameSet(schedulerName),
                () -> String.join(",", schedulerNames()),
                dropFirstName(schedulerName)));

        // 8. 能力清单：点名的 REST 控制器集合 = z-mist-web 下的 Controller 类集合
        Pattern controllerName = Pattern.compile("`([A-Z][A-Za-z0-9]*Controller)`");
        list.add(new Claim("REST 控制器集合",
                (readme, label) -> tableBlock(readme, Pattern.compile("^\\|\\s*能力\\s*\\|\\s*入口\\s*\\|"), label),
                nameSet(controllerName),
                () -> String.join(",", webControllerNames()),
                dropFirstName(controllerName)));

        return list;
    }

    /** 逐条对账：README 的值 == 源码重算值（一次全报：把不一致全列出来再抛）。 */
    private static void checkClaims(String readme, List<Claim> claims) {
        List<String> mismatches = new ArrayList<>();
        for (Claim claim : claims) {
            Block block = claim.anchor.locate(readme, claim.label);
            String actual = claim.extract.extract(block, claim.label);
            String expected = claim.expected.get();
            if (!expected.equals(actual)) {
                mismatches.add(claim.label + " —— README 写 " + actual + "，源码重算 " + expected);
            }
        }
        if (!mismatches.isEmpty()) {
            throw new AssertionError("README 定量主张与源码重算值不一致（" + mismatches.size() + " 条）：\n  - "
                    + String.join("\n  - ", mismatches));
        }
    }

    // ------------------------------------------------------------------ 用例

    /** 用例 A：真实 README 的所有主张与源码重算值逐条对账 —— 必须全绿。 */
    @Test
    void readmeQuantitativeClaimsMustMatchSource() {
        checkClaims(readmeText(), claims());
    }

    /** 用例 B（牙口）：每条主张都合成一个改错的 README，必须抛 AssertionError，且报错只指向那条主张。 */
    @Test
    void mutatedReadmeMustBeRejected() {
        String readme = readmeText();
        List<Claim> claims = claims();
        // 基线必须先健康，否则「报错指向哪条主张」的判断没意义
        assertDoesNotThrow(() -> checkClaims(readme, claims), "基线 README 自身已漂移，先把 README 修回与源码一致");
        for (Claim claim : claims) {
            Block block = claim.anchor.locate(readme, claim.label);
            String broken = claim.mutate.mutate(readme, block, claim.label);
            assertNotEquals(readme, broken, "变异没有生效：" + claim.label);
            AssertionError error = assertThrows(AssertionError.class, () -> checkClaims(broken, claims),
                    "主张被放过（README 写错但没红）：" + claim.label);
            assertTrue(error.getMessage().contains(claim.label),
                    "报错没指向真正对不上的主张：" + claim.label + " / " + error.getMessage());
            for (Claim other : claims) {
                if (!other.label.equals(claim.label)) {
                    assertTrue(!error.getMessage().contains(other.label),
                            "报错混进了别的主张（" + other.label + "）：" + error.getMessage());
                }
            }
        }
    }

    /** 用例 C（不许冤枉健康页）：真实 README 不得被判红，且每条主张都必须真的定位到并抠出值。 */
    @Test
    void realReadmeMustNotBeFlagged() {
        String readme = readmeText();
        List<Claim> claims = claims();
        for (Claim claim : claims) {
            Block block = claim.anchor.locate(readme, claim.label);
            String value = claim.extract.extract(block, claim.label);
            assertTrue(value != null && !value.isEmpty(), "主张没抠出值（守卫空跑）：" + claim.label);
        }
        assertDoesNotThrow(() -> checkClaims(readme, claims), "真实 README 被误判为红");
    }
}
