# Aulama Anime TV

![Aulama Anime TV 封面](./docs/assets/cover.png)

Aulama Anime TV 係為 Android TV 同電視盒子整理嘅動畫瀏覽及播放介面，針對遙控器焦點、連續長按、橫向片單同選集流程優化。

目前版本：`2.6.0`（`versionCode 1008`）

## 功能特色

- Home、Detail、Search、History、Timeline、Category 採用共用深色 TV 介面
- 焦點、封面、選集介面針對 DPAD 遙控器調整
- 內建繁體中文字串與常見簡體內容轉換
- 支援搜尋、切換來源、播放記錄、選集及續播
- 自動檢查 GitHub Release，新版本可直接在 TV 下載並安裝
- 畫面只呈現來源實際提供嘅集數、年份及狀態資料；缺失欄位會隱藏
- TV 版本不包含額外升頻 shader，避免額外 GPU 負擔

## 安裝

1. 到右邊 / 上方的 Releases 頁下載最新 APK
2. 將 APK 安裝到 Android TV 或電視盒子
3. 首次開啟後按需要設定來源與網路

## 專案說明

- 本專案以 Android TV 使用體驗為優先，屬於客製化公開版本
- 品牌介面、遙控操作、繁體中文同播放器 UI 均有額外調整
- 網站內容與實際可播放狀態會受來源站點、網路環境、區域限制影響

## 致謝與引用

本專案參考並延伸以下第三方公開項目與資源：

- [peacefulprogram/sakura-animation](https://github.com/peacefulprogram/sakura-animation)
感謝原作者公開相關項目，令 Android TV 動漫播放器方向有更清晰嘅參考基礎。  
本 repo 主要集中喺 Aulama Anime TV 品牌介面、遙控操作、繁體中文與播放體驗調整。
