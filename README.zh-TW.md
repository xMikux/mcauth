# MinecraftAuthentication (Fork)

> [!WARNING]
> 此 Fork 由 AI 輔助開發（Vibe Coding），使用 Claude 與 Gemini。程式碼品質與穩定性**不作任何保證**，請自行評估風險後使用。

這是 [minecraftauth.me](https://minecraftauth.me) 官方插件的 Fork 版本，擴充了更廣泛的平台支援、改善程式碼品質，並建立了 E2E 測試基礎設施。由 xMikux 使用 Claude 與 Gemini 輔助開發。

> For English, see [README.md](README.md)

---

## 與原版的主要差異

### 平台支援

| 平台 | 原版 | 本 Fork |
|------|------|---------|
| Bukkit / Spigot / Paper | ✅ | ✅ |
| Folia | ❌ | ✅ |
| BungeeCord | ✅ | ❌ (已移除) |
| Velocity | ✅ | ✅ |
| Sponge | ✅ | ❌ (已移除) |
| Forge 1.16.5 | ✅ | ❌ (已移除) |
| Forge 1.18.2 | ✅ | ✅ |
| Forge 1.19.3 | ✅ | ❌ (已移除) |
| Forge 1.20.1 | ✅ | ✅ |
| Forge 1.21.1 | ❌ | ✅ |
| Fabric（MC 1.18 – 1.21.1，通用模組） | ❌ | ✅ |
| Fabric 1.21.11 | ❌ | ✅ |
| NeoForge 1.21.1 | ❌ | ✅ |

### 功能改進

- **Folia 支援**：改善 Bukkit 模組的執行緒安全性，支援 Folia 伺服器
- **Bug 修復**：修正 `/minecraftauth` 指令不帶參數時發生的 `ArrayIndexOutOfBoundsException`
- **Java 21**：將整個專案升級至 Java 21
- **精簡專案**：移除 BungeeCord 與 Sponge 模組以降低維護成本

### E2E 測試基礎設施

新增 `test-server-runner` 模組，針對真實伺服器核心進行端對端整合測試：

- 自動下載 Paper、Fabric、NeoForge 伺服器 jar
- 自動抓取最新 Fabric Loader / NeoForge 版本號
- 使用 JUnit `@ParameterizedTest` 跨多個 MC 版本測試
- 模擬真實玩家連線行為
- 支援平行執行（動態 port 分配）
- 統一的 log 監控機制，跨所有平台偵測錯誤

---

## 模組結構

```
minecraftauthentication/
├── common/              # 共用邏輯（Gatekeeper、認證服務）
├── server/
│   ├── bukkit/          # Bukkit / Spigot / Paper / Folia
│   ├── fabric/
│   │   ├── 1.21.1/      # MC 1.18 – 1.21.1 通用模組
│   │   └── 1.21.11/     # 因 MC 1.21.11 API 變更獨立出的模組
│   ├── forge/
│   │   ├── 1.18.2/
│   │   ├── 1.20.1/
│   │   └── 1.21.1/
│   └── neoforge/
│       └── 1.21.1/
├── proxy/
│   └── velocity/
└── test-server-runner/  # E2E 整合測試
```

---

## 建置

需要 Java 21。

```bash
./gradlew build
```

建置產出位於各模組的 `build/libs/` 目錄。

---

## 原始專案

- 官網：https://minecraftauth.me
