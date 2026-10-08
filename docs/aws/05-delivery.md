# 05 DNS・HTTPS・画面配信設計

## ドメイン・証明書

以下のFQDNは例であり、取得・確定済みではない。

| 用途 | 例 | 証明書リージョン |
|---|---|---|
| 利用者向けURL | shop.example.com | CloudFront用：us-east-1 |
| ALB origin | origin.example.com | ALB用：ap-northeast-1 |

Route 53 Public Hosted Zoneで管理。ドメイン登録とNS委任は外部前提として記録し、CDKがドメイン購入も自動実行する設計にしない。既存zoneがある場合はID・zone名でimportし重複作成しない。

ACMはDNS検証。CloudFront用とALB用をそれぞれCDK管理する。CloudFront用証明書は専用us-east-1 stackで先に作成し、ARNを設定値として東京stackへ渡す。実験的crossRegionReferencesへの依存を避ける初期案。証明書置換時は新ARN→Distribution更新→旧証明書削除の順。

## CloudFront behaviors

| パス | Origin | キャッシュ | 転送・メソッド |
|---|---|---|---|
| /api/*（/apiも必要なら明示追加） | ALB / origin.example.com | CachingDisabled：min/default/max TTL=0 | 全HTTPメソッド、必要Cookie・query・header |
| /images/* | 商品画像S3 | オブジェクト名version化、長期cache案 | GET/HEAD、Cookieなし |
| Default | frontend S3 | HTMLは短期または再検証、CSS/JSはversion名で長期 | GET/HEAD、Cookieなし |

ViewerはHTTPSへ誘導。APIクライアントは最初からHTTPS URLを使用。ALB originもHTTPS-only。origin hostnameと証明書名を一致させ、ブラウザ側Hostをそのままforwardしない構成を基本案とする。

APIはユーザー別データ・Set-Cookieを共有キャッシュしない。バックエンドもCache-Control: no-storeを返す。CloudFrontでminimum TTLが正だとno-storeでも保持され得るため必ず0。Cookie転送とキャッシュ無効化は別設定として確認する。

転送対象案：USER_SESSION、ADMIN_SESSION、ゲスト識別Cookie、検索/継続情報等query、Content-Type、Accept、Origin、CSRF header。具体名はAPI設計で確定する。ブラウザ向けURLは同一originの `/api/...` とし、本番相当ではCORSを不要にする。ローカル開発は許可originを限定し、credentials使用時のwildcardを禁止する。

## HTMLページのURL

React SPAを前提とした「すべてindex.htmlへ返す」設定は採用しない。各画面をHTMLファイルとして配信するMPAを初期案とする。例：`/product-list.html`、`/admin/login.html`。拡張子なしURLにする場合はCloudFront Function等による明示的な書換を追加設計する。

S3の不存在は権限設定により403となる場合もある。APIの403まで画面HTMLに変換するDistribution全体の一律エラー応答は採用しない。静的ページの未検出とAPIエラーを分離する。未検出HTMLへのルーティングはURL一覧確定後に実装する。

管理画面HTMLの存在自体は認可境界ではない。管理APIが必ず管理者認可を行う。ブラウザへの初期HTMLに個人情報・秘密情報を埋め込まない。

## 検証

異なる会員で同じAPI URLを呼んでも内容・Cookieが混ざらない。login/logoutのSet-Cookieが到達する。POST/PUT/DELETE、query、CSRF headerが欠落しない。APIの401/403/404/500がHTMLに変換されない。S3の直接URLは拒否、正規CloudFrontは成功。

## 参考

- https://docs.aws.amazon.com/AmazonCloudFront/latest/DeveloperGuide/cache-key-understand-cache-policy.html
- https://docs.aws.amazon.com/AmazonCloudFront/latest/DeveloperGuide/Cookies.html
- https://docs.aws.amazon.com/AmazonCloudFront/latest/DeveloperGuide/restrict-access-to-load-balancer.html
- https://docs.aws.amazon.com/cdk/api/v2/java/software/amazon/awscdk/services/certificatemanager/package-summary.html
