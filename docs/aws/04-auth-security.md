# 04 認証・セキュリティ設計

## Cognito（確定方針と設定案）

| 項目 | 会員Pool | 管理者Pool |
|---|---|---|
| 分離 | 独立User Pool | 独立User Pool |
| ID | メールアドレス | メールアドレス |
| 登録 | 独自登録画面からバックエンド経由 | 自己登録なし、初期運用で1名作成 |
| メール確認 | 標準送信・標準文面、確認コード | 初期作成手順で確認状態を管理 |
| App client | バックエンド専用 | バックエンド専用 |
| Hosted UI | 使用しない | 使用しない |
| 権限 | 会員の本人データのみ | マスター管理者の権限をAPIで検証 |

Pool設定・App Client・管理者GroupはCDKで管理。個人のメール・パスワードはCDKソース・contextへ置かない。初期管理者作成は別手順で、初回パスワード変更challengeが画面未対応のまま残らないよう状態を確認する。

バックエンド用の認証フロー案はAdminInitiateAuth / ADMIN_USER_PASSWORD_AUTH。管理者・会員のPool/ClientをAPI経路ごとに固定し、ブラウザが任意Pool IDを指定できない設計とする。実装時にSDK・更新フローの組み合わせを確認する。App client secret採用時はバックエンドだけで管理し、生成方法とSecret保管時の露出範囲を確認する。

パスワード要件：16〜30文字、半角英大文字・英小文字・数字を各1文字以上、記号任意、空白不可。Cognitoの最小長・文字種設定に加えて、最大30文字・許可文字・空白不可は登録APIでも検証する。Cognito設定だけで全条件を満たすと見なさない。ログイン時は入力をトリム・変換しない。

トークン設定案：Access/ID token 1時間、Refresh token 12時間。会員アプリセッションの絶対期限は別途12時間で管理。Refresh tokenのrotation採否・更新API・失効手順は実装時にセットで確定。管理者の期限は初期案1時間で、未確定。

## Cookie・セッション

| 項目 | 設定案 |
|---|---|
| 会員Cookie | USER_SESSION |
| 管理者Cookie | ADMIN_SESSION |
| Cookie属性 | Secure、HttpOnly、SameSite=Lax、Domain省略 |
| Path | /api/user/、/api/admin/に分離する案（API一覧と同期） |
| 保存先 | PostgreSQL / Spring Session JDBC |
| 期限 | 会員はログイン成功時刻+12時間。操作・token refreshで延長しない |
| 再起動 | session Cookieを初期案とし、ブラウザ復元に依存せずサーバー期限を強制 |
| ログイン成功時 | session IDを再発行し、固定化を防止 |
| ログアウト | 対象セッション削除、Cookie失効、必要なCognito失効。会員カートは維持 |

CookieのPathだけではセキュリティ境界にならない。独立した認証設定・session namespace・認可ルールをアプリで実装する。同一Spring Bootで2系統を扱う場合、Spring Sessionの標準設定1組だけで分離できると仮定せず、SecurityFilterChainとsession repositoryの設計・テストを行う。

CSRF対策は必須。JavaScriptが専用APIからCSRF tokenを取得し、変更系リクエストのheaderへ設定する案。セッションCookieはJavaScriptから読まない。SameSiteだけに依存しない。許可origin・Fetch Metadata等も共通API設計で整理する。

Cognitoトークンはサーバー側のみ保持。DB保存する場合は保存暗号化・鍵管理を検討し、ログ・API応答・HTMLへ出さない。パスワードは永続化しない。

## IAM・運用

CDK deploy role、ECS execution role、ECS task role、Lambda role、EC2 SSM roleを分離する。バックエンドでは認証だけでなく、会員の所有権と管理者権限を毎回確認する。一般会員Poolのtokenを管理者用として受け入れない。

秘密値をGit、CloudFormation Outputs、CDK contextに記載しない。Secretを参照するコードでも合成テンプレート・CloudFormation設定に値が残る場合があるため、閲覧権限を制限する。特にCloudFront→ALB専用headerは完全に非表示の秘密と見なさず、アクセス権と定期変更で保護する。

## 検証

2Poolの取り違え拒否、会員/管理者同時ログイン、片方だけのログアウト、12時間経過、CSRF拒否、Cookie再利用拒否、メール未確認、カート統合失敗時のログイン維持を確認する。

## 参考

- https://docs.aws.amazon.com/cognito/latest/developerguide/amazon-cognito-user-pools-authentication-flow-methods.html
- https://docs.aws.amazon.com/cognito/latest/developerguide/user-pool-settings-policies.html
- https://docs.aws.amazon.com/cognito/latest/developerguide/user-pool-settings-client-apps.html
