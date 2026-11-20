# AWS設計書 — cloudnative-ec-java

版：0.1 / 作成日：2026-10-06 / 状態：IaC実装前の設計ドラフト

## 目的と管理方法

AWS CDK v2（Java）で構築するための設定・依存関係・責務・検証条件を定義する。実装済み・デプロイ済みを示す資料ではない。設定表の「案」は未承認の推奨初期値であり、確定事項と区別する。

既存リポジトリの `docs/`、`ecs/`、`frontend/`、`iac/` を維持する。本一式は `docs/aws/` に配置する。CDKコードは `iac/`、Spring Bootは `ecs/`、HTML・CSS・JavaScriptは `frontend/` に配置する。設計書とコードは同じPRで更新する。

## 文書一覧

| 文書 | 対象 |
|---|---|
| [01-network.md](01-network.md) | VPC・サブネット・ルート・SG・SSM |
| [02-compute.md](02-compute.md) | ECS・ALB・ECR・踏み台EC2 |
| [03-data-storage.md](03-data-storage.md) | RDS・S3・バックアップ |
| [04-auth-security.md](04-auth-security.md) | Cognito・Cookie・IAM・秘密情報 |
| [05-delivery.md](05-delivery.md) | DNS・証明書・CloudFront・静的ページ |
| [06-operations.md](06-operations.md) | SQS・Lambda・監視・停止・復旧 |
| [07-iac.md](07-iac.md) | CDKの構成・変数・デプロイ順序 |

## 確定事項

- フロントエンド：HTML・CSS・JavaScript。React・TypeScriptは使用しない。
- バックエンド：Java / Spring Boot。AWS CDKもJavaで記述するが、別プロジェクトとする。
- 独自画面でAmplify Authを使用し、会員・管理者は別Cognito Pool。保護APIはGatewayでJWTを確認。
- 会員のメール確認：Cognito標準メール・標準文面。確認完了後はログイン画面へ戻り、自動ログインしない。
- ログイン状態はAmplify AuthとCognitoのトークン期限に従う。ECSはステートレス、RDSに認証セッションを保存しない。
- RDSはプライベート配置。DB管理はEC2踏み台を使用。Route 53・ACMを採用。
- ゲストカート：最後の正常な更新から7日。参照では延長しない。会員のログアウトで会員カートを削除しない。
- 決済処理のみモック。カード情報は入力・保存しない。実決済・発送なし。成立済み注文のメールはSESサンドボックスで検証済みの購入者宛先に送る。
- 注文受付→手配中を含む注文状況変更は管理者操作。SQSによる自動更新なし。

## 基本構成案

小規模検証環境1つを想定。東京リージョン、ECS Fargate 1タスク、RDS Single-AZを初期案とする。ECS・EC2はPublic subnetに置き、受信をSGで制限する。RDSはPrivate isolated subnetに置く。NAT Gatewayは初期案に含めない。可用性を保証する本番構成ではない。

```mermaid
flowchart TD
  Browser["HTML / CSS / JavaScript"] --> CF[CloudFront]
  CF -->|静的ファイル| S3["S3 / OAC"]
  CF --> G["API Gateway：公開／会員JWT／管理者JWT"]
  G --> V["VPC Link"]
  V --> ALB["内部ALB"]
  ALB --> ECS["ECS / Spring Boot"]
  Browser -->|Amplify Auth| Cognito["Cognito / 2 Pools"]
  ECS -->|業務データ| RDS["RDS PostgreSQL"]
  ECS -->|注文コミット後Publish| SNS[SNS]
  SNS --> SQS[SQS]
  SQS --> Lambda[Lambda]
  Lambda --> SES[SES]
  Operator[管理者端末] --> SSM[Session Manager]
  SSM --> EC2[踏み台EC2]
  EC2 --> RDS
```

DNSはRoute 53、TLS証明書はACM。ログ・メトリクスはCloudWatch。図は主要経路を示し、応答・IAM・証明書の関連付け等は省略している。

## IaCとアプリケーションの境界

| IaCが管理 | アプリ・運用が管理 |
|---|---|
| VPC・SG・RDS本体・バックアップ設定 | DBスキーマのマイグレーション、税計算、在庫ロック |
| Cognito Pool / App Client / JWT Authorizer | 認証とトークン期限、ゲストCookieのOrigin確認、業務認可 |
| SNS / SQS / DLQ / Lambda / SES / IAM | 注文成立後の購入完了メール、重複・結果不明の運用 |
| ECS / ALB / CloudFront / S3 | アプリイメージ・静的ファイルのリリース |
| 管理者Pool・権限設定の枠 | 初期管理者1名の作成とパスワード設定 |

## 実装前の確認事項

| 項目 | 初期案・確認内容 | 状態 |
|---|---|---|
| AWSアカウント・環境名 | 個人の検証環境 `dev` 1つ | 未確定 |
| ドメイン・Hosted Zone | 取得済みか、新規購入か。ドメイン名を確定 | 未確定 |
| リージョン | 主系ap-northeast-1、配信用ACM us-east-1 | 案 |
| CIDR・AZ | 01-networkの表。既存ネットワークと重複しないこと | 案 |
| ECS・DB容量 | 02/03の初期サイズ。負荷確認後に調整 | 案 |
| 管理者トークン期限 | 管理者用App Clientで定義。旧アプリセッション期限は適用しない | 認証共通設計 |
| 稼働時間・月額上限 | 常時か検証時のみか、通知先と予算額 | 未確定 |
| 管理者MFA | 初期導入の有無。設計・画面追加への影響を確認 | 未確定 |
| Java・CDK・PostgreSQLバージョン | 実装時の対応状況を確認して具体バージョン固定 | 未確定 |
| イベント送信の再送保証 | SNS受付結果不明は自動再発行しない。注文と通知を独立させる | 確定 |

## 更新履歴

| 日付 | 版 | 変更 |
|---|---|---|
| 2026-10-06 | 0.1 | CDK Java、HTML/CSS/JavaScript、Cookie＋PostgreSQLセッションに合わせて初版作成 |
