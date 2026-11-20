# 02 コンピュート設計

## ECS Fargate

| 項目 | 初期設定案 |
|---|---|
| OS / CPU | Linux / X86_64 |
| CPU / メモリ | 0.5 vCPU / 1 GiB |
| タスク数 | desiredCount=1、初期はAuto Scalingなし |
| 配置 | Public subnet、assignPublicIp=true |
| コンテナポート | 8080 |
| プロセス | Java Spring Boot、非root実行 |
| イメージ | ECR、Git commit単位のimmutable tagまたはdigest固定 |
| ログ | awslogs → CloudWatch Logs、保持14日案 |
| デプロイ | rolling update、circuit breaker + rollback |
| 更新中タスク数 | 最小正常100%、最大200%案。一時的に2タスク分課金 |
| ヘルスチェック猶予 | 120秒案。起動時間計測後に調整 |

1 GiB内でJVM heap・metaspace・native memoryを含める。heapに全メモリを割り当てない。負荷試験でOOMが出る場合は2 GiB等へ変更し費用表も更新する。

## ALB・Target Group

internal ALBをApp-A/Bに配置。API Gateway HTTP APIのVPC Linkをlistener ARNに接続する。link SGからのみTCP80を許可する。

Target typeはIP。内部ALBからタスクのHTTP 8080へ転送する。業務ルートを公開ALBで迂回できる設定を設けない。

| ヘルスチェック | 設定案 |
|---|---|
| Path | /actuator/health/readiness |
| 成功 | HTTP200 |
| Interval / timeout | 30秒 / 5秒 |
| Healthy / unhealthy threshold | 2回 / 3回 |

アプリ側でreadiness endpointを有効化し、必要なDB準備状態を反映する。詳細情報や全Actuator endpointは公開しない。独自に `/health` を採用するならCDK設定とアプリ実装を同時変更する。

## IAMの分離

- Task execution role：ECR pull、ログ初期化、起動時に注入するSecret参照。
- Task role：注文通知Topicのsns:Publish、商品画像S3操作など実行する業務だけ。SQS投入はSNS購読で行う。
- Secretsを実行時にSDKで取得する場合はTask role側、ECS secret injectionの場合はexecution role側に必要権限を設定する。

## ECR・踏み台

ECRはタグ上書き防止、push時スキャン、古い未使用イメージ削除ルールを設定。直近の復旧対象イメージは残す（10世代案）。

踏み台：Amazon Linux、t3.micro、暗号化gp3 8 GiB、IMDSv2必須、SSH鍵なし、SSM管理。OS更新は運用手順で実施。EC2停止をCDK deployと同一視しない。

## リリース境界と検証

CDKはECR/ECS等の枠を作成。アプリビルド・ECR pushは別手順。初回は空のECRに存在しないtagを指定してServiceを起動しない。リポジトリ作成→イメージpush→Service作成の順とする。

検証：ALB health、ローリング復旧、再起動後も同一JWTで権限確認できること。DBセッションの作成・参照なし。

## 参考

- https://docs.aws.amazon.com/AmazonECS/latest/developerguide/task_execution_IAM_role.html
- https://docs.aws.amazon.com/AmazonECS/latest/developerguide/deployment-circuit-breaker.html
