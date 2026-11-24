# CloudCart の CDK 開発環境

リポジトリ直下の `docker-compose.yml` と、この `iac/` を使用します。以下のコマンドは **WSLでリポジトリ直下から**実行してください。APIのソースは別の `ecs/` に置きます。IaCだけ試す場合は、Spring Bootプロジェクトも起動中のDBも不要です。

## 初回：コンテナを準備する

Docker DesktopのWSL連携とDocker Compose v2が前提です。既存のルートの `.env` は残してください。共有のComposeでは、サービス選択前に `POSTGRES_PASSWORD` の指定を検証します。未設定なら `.env` にローカルDB用の値を設定してください（この値はiacには渡されません）。`.env` はGitに含めません。

```bash
docker compose build iac
docker compose run --rm iac cdk --version
docker compose run --rm iac java -version
docker compose run --rm iac node --version
```

コンテナは非rootで実行します。WSLの `id -u` または `id -g` が1000以外の場合は、初回ビルド前に `.env` の `LOCAL_UID` と `LOCAL_GID` に各数値を指定してください。

## AWSに変更を加えずに動作を確認する

```bash
docker compose run --rm iac cdk synth --no-lookups
```

初回はGradle本体とJavaライブラリをダウンロードするため、インターネット接続が必要です。生成物はホストの `iac/cdk.out/CloudCartStorageStack.template.json` に残ります。出力の `Outputs.WebBucketName.Value` が `cloudcart-lab.click` であれば、JavaのCDKアプリからテンプレートを生成できています。サンプルはバケット名を参照するだけなので、AWS上でバケットの存在確認・作成・削除・ポリシー変更は行いません。実際のバケット設定をCDKで管理する場合は、別途、実設定に合わせた定義と既存リソースのインポートを行います。

## AWSと比較するときだけ認証を渡す

通常の `build` や上記の `synth` ではAWSの認証情報をマウントしません。以下は、WSLで設定済みの名前付きプロファイルを使う例です。`cloudcart-dev` は実際のプロファイル名に置き換えてください。IAM Identity Center（SSO）を使っている場合、先にWSLでログインしてからマウントします。

```bash
aws sso login --profile cloudcart-dev  # SSOプロファイルの場合のみ
aws sts get-caller-identity --profile cloudcart-dev
docker compose run --rm \
  -v "$HOME/.aws:/home/cdk/.aws:ro" -e AWS_PROFILE=cloudcart-dev \
  iac cdk diff CloudCartStorageStack
```

有効期限切れで読み取り専用のキャッシュを更新できない場合は、WSLで再ログインしてください。CI/CDではこのローカルのマウントを使わず、CI用のIAMロールの一時認証情報を利用します。実デプロイには対象アカウント／リージョンのbootstrapを事前に確認します。

## ファイルの役割

| ファイル | 役割 |
| --- | --- |
| ../docker-compose.yml | `iac` を明示したときだけ起動。app/dbとは独立。東京リージョンが既定。 |
| Dockerfile | Corretto 21、Node.js 24、CDK CLI 2.1149.0。ホストへJDKやNode.jsを入れる必要はありません。 |
| cdk.json | CLIからGradle Wrapperを使ってJavaのエントリーポイントを実行。 |
| build.gradle、gradlew、gradle/wrapper/ | Java 21、Gradle 9.2.1、CDKライブラリ2.278.0を使用。WrapperはGitに含めます。 |
| src/main/java/com/cloudcart/infra/CloudCartApp.java | CloudCartStorageStackを定義。既存バケット名を出力する開始例。 |

このコンテナはAWSインフラの開発ツールです。ECSに配布するJava APIのイメージではありません。
