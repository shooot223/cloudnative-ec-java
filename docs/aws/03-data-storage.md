# 03 DB・ストレージ設計

## RDS PostgreSQL

| 項目 | 設定案・方針 |
|---|---|
| Engine | PostgreSQL、具体バージョンは実装時固定 |
| Instance | db.t4g.micro / Single-AZ |
| Storage | gp3 20 GiB、暗号化有効 |
| Public access | false（確定） |
| 配置 | 2AZのPrivate DB subnet group |
| Backup | 自動バックアップ7日案 |
| 削除 | deletionProtection=true、CDK removalPolicy=SNAPSHOT案 |
| Storage autoscaling | 初期上限50 GiB案。増加後の縮小不可を考慮 |
| メンテナンス | 検証時間外のUTC windowを実装時固定 |
| DB接続 | TLS使用、証明書検証有効 |

DB名・接続数上限・poolサイズ・具体windowは実装時に確定。小さいDBのため接続poolは小さく開始し、ECS更新中2タスクと踏み台接続の合計を考慮する。RDSマスター資格情報はSecrets Manager管理を基本案とする。アプリ用には別の最小権限DBユーザーを作成し、マスターで常時接続しない。

DBのtimezoneはUTC。日時はtimestamptzを基本に、APIはUTCのISO8601、画面でJST表示。取得時に常に自動JST変換される設計とはしない。詳細は共通API・DB設計と同期する。

## セッション・スキーマ管理

Spring Session JDBCのテーブルとアプリ独自の絶対期限情報をPostgreSQLで管理する。12時間の絶対期限とidle timeoutは別概念。idle設定だけで「ログインから12時間」を実装しない。

マイグレーションツールでDDLを履歴管理し、リリース時の単一ジョブで実行する。各ECSタスクが競合して初期DDLを実行しない。CDKは業務テーブルや初期管理者パスワードを定義しない。

## S3

| バケット | 保存対象 | 方針 |
|---|---|---|
| frontend | HTML・CSS・JavaScript・共通画像 | Private、Block Public Access、OACのみ読み取り |
| product-images | 商品画像 | Private、公開用prefixのみCloudFront OAC読取 |
| CDK bootstrap | CDK assets | アプリ用バケットとは分離、CDK管理 |

S3 website endpointは使わずREST originを使用。SSE-S3を初期案とする。frontendと商品画像はversioning有効案、古い非現行バージョンは30日で整理する案。削除・更新方針は保存容量と復旧要件を確認する。CDK removalPolicy=RETAIN、autoDeleteObjects=falseを基本とする。

非公開商品の画像も推測URLで取得できない要件なら、単純な公開用CloudFront配信では足りない。初期案は非公開・未公開画像を公開用prefixへ置かず、公開後削除時のキャッシュ無効化まで設計する。画像の非公開性の要件は実装前に確認する。

## 検証・復旧

スナップショットから別DBへ復元できること、接続先Secret変更でアプリが切り替わることを確認。S3は過去version復元後にCloudFrontのキャッシュも更新する。DB復旧後はセッションを無効化して再ログインを求める運用を基本案とする。

## 参考

- https://docs.aws.amazon.com/AmazonRDS/latest/UserGuide/USER_WorkingWithAutomatedBackups.html
- https://docs.aws.amazon.com/AmazonCloudFront/latest/DeveloperGuide/private-content-restricting-access-to-s3.html
