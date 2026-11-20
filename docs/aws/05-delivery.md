# 05 DNS・HTTPS・画面配信設計

## ドメイン・証明書

以下のFQDNは例であり、取得・確定済みではない。

| 用途 | 例 | 証明書リージョン |
|---|---|---|
| 利用者向けURL | shop.example.com | CloudFront用：us-east-1 |
| API origin | HTTP APIのリージョナルエンドポイント | AWS提供のHTTPS endpoint |

Route 53 Public Hosted Zoneで管理。ドメイン登録とNS委任は外部前提として記録し、CDKがドメイン購入も自動実行する設計にしない。既存zoneがある場合はID・zone名でimportし重複作成しない。

CloudFrontの独自ドメイン証明書はus-east-1でDNS検証。HTTP APIを公開originとして使用し、内部ALB用の公開DNS名は作成しない。

## CloudFront behaviors

| パス | Origin | キャッシュ | 転送・メソッド |
|---|---|---|---|
| /api/* | API Gateway HTTP API | CachingDisabled、全TTL=0 | 全業務メソッド。Authorization・Origin・Referer・Cookie・Idempotency-Key・queryを保持。viewer Hostを転送しない |
| /product-images/* | 商品画像S3 | オブジェクト名version化、長期cache案 | GET/HEAD、Cookieなし |
| Default | frontend S3 | HTMLは短期または再検証、CSS/JSはversion名で長期 | GET/HEAD、Cookieなし |

ViewerとGateway origin間はHTTPS。GatewayのVPC Linkから内部ALBへ接続する。

APIはユーザー別データ・Set-Cookieを共有キャッシュしない。バックエンドもCache-Control: no-storeを返す。CloudFrontでminimum TTLが正だとno-storeでも保持され得るため必ず0。Cookie転送とキャッシュ無効化は別設定として確認する。

ユーザーと管理者はBearer JWT、ゲストはec_guest_cart_token。構造を変えずに転送し、Set-Cookieを利用者間でキャッシュしない。

## HTMLページのURL

React SPAを前提とした「すべてindex.htmlへ返す」設定は採用しない。各画面をHTMLファイルとして配信するMPAを初期案とする。例：`/product-list.html`、`/admin/login.html`。拡張子なしURLにする場合はCloudFront Function等による明示的な書換を追加設計する。

S3の不存在は権限設定により403となる場合もある。APIの403まで画面HTMLに変換するDistribution全体の一律エラー応答は採用しない。静的ページの未検出とAPIエラーを分離する。未検出HTMLへのルーティングはURL一覧確定後に実装する。

管理画面HTMLの存在自体は認可境界ではない。管理APIが必ず管理者認可を行う。ブラウザへの初期HTMLに個人情報・秘密情報を埋め込まない。

## 検証

検証：会員・管理者・ゲストの識別、Cookie発行と解除、Authorization・Origin・Idempotency-Keyの伝達、APIエラーがHTMLへ置換されないこと。画像は/product-images/のS3キーを変更せず転送。

## 参考

- https://docs.aws.amazon.com/AmazonCloudFront/latest/DeveloperGuide/cache-key-understand-cache-policy.html
- https://docs.aws.amazon.com/AmazonCloudFront/latest/DeveloperGuide/Cookies.html
- https://docs.aws.amazon.com/AmazonCloudFront/latest/DeveloperGuide/restrict-access-to-load-balancer.html
- https://docs.aws.amazon.com/cdk/api/v2/java/software/amazon/awscdk/services/certificatemanager/package-summary.html
