# 06 非同期処理・監視・運用設計

## 同期・非同期の境界

注文成立、モック決済結果の確定、在庫減算、カート反映はAPI側の整合性ある同期処理として設計する。DBの悲観ロック・transaction・注文のべき等性は機能設計で定義する。

SQS/Lambdaは注文成立後の通知ログ出力に限定。注文状況変更・在庫減算を行わず、実際の購入完了メールは送信しない。ログイベント送信失敗を理由に成立済み注文を「注文失敗」にしない。

| 項目 | 設定案 |
|---|---|
| Queue | Standard queue、暗号化有効 |
| 保持期間 | 通常4日 / DLQ14日 |
| Lambda timeout | 30秒 |
| Visibility timeout | 180秒以上、batch window分も考慮 |
| Batch | 初期1件、処理安定後に調整 |
| maxReceiveCount | 5 |
| Lambda配置 | VPC外。通知ログのみならDB接続不要 |
| Event | eventId、orderId、eventType、occurredAt、schemaVersion |

イベントには氏名・住所・メール・カード番号を含めない。重複配送を想定する。初期案では通知ログの重複をeventIdで識別できる設計とし、exactly-onceを保証しない。

DB commitとSQS送信は同一transactionではない。初期案はログ用途として送信失敗を監視し、注文IDから運用再送できることを受入条件とする。配信漏れを許容しない要件ならtransactional outboxと再送処理を追加し、費用・構成図を更新する（未確定）。

## 監視・通知

| 対象 | 監視案 |
|---|---|
| ALB | unhealthy targets、5xx、response time |
| ECS | desired/runningの差、CPU・memory、起動失敗 |
| RDS | CPU、空きmemory/storage、接続数、障害イベント |
| SQS | oldest message age、DLQメッセージ数>0 |
| Lambda | Errors、Throttles、duration |
| 費用 | AWS Budgetsの月額通知 |

CloudWatch Logsは14日保持案。通知先メール・予算閾値は未確定。CloudWatch Alarmをメール通知するならSNS topic/subscriptionを追加する。SNS確認メールへの承認は人の操作。これらは追加採用案として費用表へ反映する。

## 停止・再開

- ECS：desiredCount=0。常駐費用を減らせるがALBは残る。
- EC2：停止。EBS等の保存費用は残る。Public IP再割当を前提とする。
- RDS：停止。ストレージ・バックアップは課金対象。停止は最大7日で自動再開するため放置しない。
- ALB：停止操作はない。残す場合は時間課金。削除する場合はDNS/CloudFront originへの影響を含む再構築手順が必要。
- NAT未採用でもPublic IPv4、ログ、S3、ドメイン等の費用は残る。

再開順はRDS→接続確認→ECS→ALB healthy→CloudFront経由確認。consoleで変更したdesiredCount等とCDK設定が食い違うと次deployで戻るため、通常は環境設定のrunning/stoppedプロファイルで管理する。RDS stop/startは別の明示運用でありCDKの通常差分更新とは分ける。

## バックアップ・削除

RDS/S3/Cognitoは原則保持。Cognitoの削除保護を有効にし、CDK RemovalPolicy.RETAINを検討する。検証環境の全削除でも、DB snapshot・S3 version・ECR image・bootstrap資材・Route53 zone・ドメイン更新費用は別確認。`cdk destroy`が費用ゼロを保証すると見なさない。

復旧手順と接続先切替を文書化。RDS復元後のsession無効化、Secret切替、ECS再起動、整合性確認を行う。メンテナンス・ログ時刻はUTC、運用表示はJSTで区別する。

## 参考

- https://docs.aws.amazon.com/lambda/latest/dg/services-sqs-configure.html
- https://docs.aws.amazon.com/AmazonRDS/latest/UserGuide/USER_StopInstance.html
- https://docs.aws.amazon.com/AmazonECS/latest/developerguide/cloudwatch-metrics.html
