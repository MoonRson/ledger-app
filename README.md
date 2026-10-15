# 极简账簿 App

把 [仓库A/one/c.html](https://moonrson.github.io/A/one/c.html)（极简账簿网页版）打包成安卓 App。

- WebView 壳 + Chart.js 已内联，**完全离线**
- 数据存 App 本地（localStorage + IndexedDB），不删 App 不丢
- 网页版数据可通过自带"备份导出/导入"迁移

## 构建

push 到 main 自动触发 GitHub Actions 编译，APK 挂在 [Releases](../../releases)。

## 数据迁移（网页版 → App）

1. 网页版：设置 → 备份导出，得到 JSON 文件
2. App：设置 → 导入备份，选该文件
