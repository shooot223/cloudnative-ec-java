# 07 AWS CDK（Java）実装設計

## ツール・リポジトリ

AWS CDK v2 / Javaを採用（確定）。`iac/`はSpring Bootから独立したJavaプロジェクト。CDK Javaの標準ひな型に合わせMavenを初期案とする。既存Gradleとの統一を優先する場合は変更可。Node.js・CDK CLIも必要。

Java、Node.js、CDK CLI、aws-cdk-lib、constructs、ビルドツールは実装開始時に対応する具体バージョンを固定。`latest`に追従して予期せぬ構成変更を起こさない。CLIとライブラリのバージョン番号が常に一致するとは限らないため互換性を確認する。

| 配置先案 | 内容 |
|---|---|
| iac/pom.xml | CDK依存関係・ビルド定義 |
| iac/cdk.json | Javaアプリ起動コマンド、feature flags |
| iac/config/dev.json | 非秘密の環境設定 |
| iac/src/main/java/.../InfraApp.java | stack生成・依存関係 |
| iac/src/main/java/.../stack/ | stack単位の定義 |
| iac/src/main/java/.../construct/ | 再利用する構成要素 |
| iac/src/test/java/ | template assertions |
| docs/aws/ | この設計書 |

この文書は実装仕様であり、上記ファイルや実行可能なCDKコードを既に作成したものではない。

## Stack分割案

| Stack | Region | 主なリソース | 先行依存 |
|---|---|---|---|
| FoundationStack | 東京 | VPC・subnet・SG・ECR・S3・Hosted Zone新設時 | なし |
| EdgeCertificateStack | us-east-1 | CloudFront用ACM | zone ID・DNS委任 |
| DataAuthStack | 東京 | RDS・Secret・Cognito 2Pool | Foundation |
| ApplicationStack | 東京 | JWT Authorizer・HTTP API・VPC Link・内部ALB・ECS・踏み台・SNS/SQS/Lambda/SES・監視 | Foundation、DataAuth、ECR image |
| DeliveryStack | 東京 | CloudFront・OAC・配信設定・DNS | Application、S3、Edge certificate ARN |

ApplicationStackがHTTP API endpointを出力し、DeliveryStackのAPI originへ渡す。一方向の依存とし、S3 OACの許可は配信元Distribution ARNだけに限定する。

## 環境変数・設定（秘密値は含めない）

| 設定キー案 | 初期値・扱い |
|---|---|
| projectName / environment | cloudnative-ec-java / dev案 |
| accountId / region | 実際のIDは未確定 / ap-northeast-1案 |
| hostedZoneId / domainName | 未確定・必須 |
| siteDomain / apiOrigin | viewer独自ドメイン／HTTP API endpoint |
| edgeCertificateArn | us-east-1 stackの出力を渡す |
| vpcCidr / availabilityZones | 01-network参照 |
| taskCpu / taskMemoryMiB / desiredCount | 512 / 1024 / 1案 |
| imageTagOrDigest | 初回push後に必ず指定 |
| dbInstanceType / engineVersion | t4g.micro案 / 実装時固定 |
| deletionProtection / logRetentionDays | true / 14案 |
| user/adminTokenValidity | それぞれのCognito App Clientで設定。ECSアプリセッションTTLを設けない |
| budgetAmount / notificationEmail | 未確定 |

Resource命名：`project-environment-purpose`。長さ制限とS3のグローバル一意性を考慮し、必要ならaccount/region等をsuffixに追加。タグ：Project、Environment、ManagedBy=CDK、Owner。個人情報・秘密値をタグへ入れない。

## 初回構築の順序

1. AWSアカウント・課金通知・認証方法・ドメイン・既存リソースを確認する。
2. 東京とus-east-1のaccount/regionを明示してCDK bootstrapする。初期IAM権限・CloudFormation実行権限を確認する。
3. FoundationStackをdiff確認後にdeploy。zone新設時はNS委任を完了する。
4. EdgeCertificateStackとDataAuthStackをdeploy。DNS検証完了・Secret・Poolを確認する。
5. backend imageをbuild/push。DB migrationとアプリ用DBユーザー準備を別手順で行う。
6. ApplicationStackをdeploy。ALB healthと認証・DB接続を確認する。
7. 証明書ARNを渡してDeliveryStackをdeploy。frontendファイルをS3へ配置する。
8. 管理者1名を運用手順で作成。メール確認・ログイン・注文・SSM・監視を結合確認する。

既存リソースがある場合は勝手に同名を再作成せず、import/reference方針を決める。CloudFormationが管理していないリソースを名前一致だけで管理下と見なさない。

## 変更・CI/CD・削除

変更はbuild/test→cdk synth→cdk diff→差分レビュー→cdk deploy。初期は手動deployで開始し、CI/CD追加時はGitHub Actions OIDC等の短期資格情報を用いる案。長期アクセスキーをリポジトリに保存しない。

CDK基盤変更とアプリリリースを分離するが、ECS image指定の変更をCDKと別pipelineが二重管理しない。初期はimage digestをCDK設定入力として更新する方式に統一する。

DataAuthの削除・置換、証明書入替、CIDR変更、公開設定変更は差分を明示的に確認する。`cdk.out/`、target/、秘密値、資格情報はGit対象外。非秘密の環境設定・固定バージョン・必要なlookup contextは再現性のため履歴管理する。

## CDK自動検証

- NAT Gatewayが0台、RDS PubliclyAccessible=false、DB暗号化有効。
- SGにDB/SSHの0.0.0.0/0受信なし。ECS受信元はALB SGのみ。
- User Poolが2つ、管理者自己登録なし。
- CloudFront API cache TTLがすべて0、必要Cookie転送あり。
- RDS/S3/Cognitoの保持・削除保護が意図どおり。
- 本番相当の設定に実際の秘密値が混入しない。

## Outputs

サイトURL、HTTP API endpoint、各Pool/Client ID、内部ALB listener、ECSサービス、SNS Topic・Queue・DLQ・ロググループを出力。認証情報・宛先・Secret値は出力しない。

## 参考

- https://docs.aws.amazon.com/cdk/v2/guide/work-with-cdk-java.html
- https://docs.aws.amazon.com/cdk/v2/guide/bootstrapping-env.html
- https://docs.aws.amazon.com/cdk/v2/guide/best-practices.html
