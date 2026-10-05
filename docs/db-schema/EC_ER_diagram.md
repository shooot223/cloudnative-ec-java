# ECサイト ER図

作成日：2026-10-05。更新済みDB設計書の12テーブル・144カラム・14外部キーを基に作成。

元資料：[DB設計書](https://docs.google.com/spreadsheets/d/1Z0vjYRgUz5CAtnzGMfQ3tNxWlo-Geof-nJeexnSFCLI/edit)

## 図の読み方

- PK：主キー、FK：外部キー、UK：一意制約、NN：NOT NULL。
- `||` は必ず1件、`o|` は0または1件、`o{` は0件以上。線端はその側の件数を示す。
- 点線は非識別関係（親キーが子の主キーに含まれないFK）。アプリ側参照を意味しない。
- 多重度はDBのFK・NULL・UNIQUE制約に基づく。成立済み注文は業務上1明細以上必須だが、FKのみでは最低1件を保証できないため図では0件以上とする。
- 分割図に同じテーブルが再登場する。DB上で重複作成する意味ではない。
- 複合UKは構成列単体の一意性を意味しない。cart_itemsは(cart_id, product_id)、order_itemsは(order_id, line_number)。

## 主要な補足

- carts.user_idとguest_cart_tokenは片方のみ設定。会員は0〜1カート、ゲストカートには会員参照がない。
- 保存配送先・保存テストカードは会員ごとに最大1件。テスト番号は全16桁を保持し、取得APIの表示項目はID・表示名・下4桁のみ。
- carts.pending_merge_cart_idは任意の自己参照。DB上は複数の参照元が同じカートを指せる。参照先がゲストであること・所有権はバックエンドで検証。
- cart_merge_logs.source_cart_idは単独UNIQUE。同一ゲストカートの再試行は同じ統合履歴を更新。
- orders.order_create_operation_idは必須FKかつUNIQUE。1操作につき注文は0〜1件。注文を持つ操作履歴は削除しない。
- 注文時の商品名・価格・税率・配送先・カード情報はスナップショット。保存配送先・保存カードへのFKは設けない。
- 注文画像はorder_items.product_idから現在の商品画像を参照する。商品は論理削除し、注文記録とのFKを維持。
- 日時はTIMESTAMPTZ(3)でUTC保持、画面でJST表示。合計・小計・返金額はBIGINT。

## 外部キーを持たない参照

| 項目 | 参照・照合先 | 扱い |
| --- | --- | --- |
| users.cognito_sub | 一般会員用Cognitoプール | 外部サービスの識別子。DBのFKではない。 |
| admins.cognito_sub | 管理者用Cognitoプール | 外部サービスの識別子。DBのFKではない。 |
| operation_histories.actor_type / actor_id | USER: users.id、ADMIN: admins.id、GUEST: carts.id | 種別に応じてバックエンドで照合。FK線は描かない。 |
| operation_histories.target_resource_id | 注文・商品等 | 操作種別に応じた参照。FK線は描かない。 |
| products.image_s3_key | S3オブジェクト、アップロード管理情報 | 現行DB設計にproduct_image_uploadsへのFKはない。 |

## 会員・保存情報・カート（キー項目）

```mermaid
erDiagram
    direction TB
    users {
        BIGINT id PK "NN / 会員ID"
        VARCHAR(128) cognito_sub UK "NN / Cognito識別子"
        VARCHAR(254) email UK "NN / メールアドレス"
    }
    user_saved_addresses {
        BIGINT id PK "NN / 保存配送先ID"
        BIGINT user_id FK, UK "NN / 会員ID"
    }
    user_saved_test_cards {
        BIGINT id PK "NN / 保存テストカードID"
        BIGINT user_id FK, UK "NN / 会員ID"
    }
    carts {
        BIGINT id PK "NN / カートID"
        BIGINT user_id FK, UK "NULL可 / 会員ID"
        VARCHAR(64) guest_cart_token UK "NULL可 / ゲストカート識別子"
        BIGINT pending_merge_cart_id FK "NULL可 / 統合待ちゲストカートID"
    }
    cart_items {
        BIGINT id PK "NN / カート明細ID"
        BIGINT cart_id FK, UK "NN / カートID / 複合UKの構成列"
        BIGINT product_id FK, UK "NN / 商品ID / 複合UKの構成列"
    }
    products {
        BIGINT id PK "NN / 商品ID"
    }
    users ||..|o user_saved_addresses : "user_id"
    users ||..|o user_saved_test_cards : "user_id"
    users o|..|o carts : "user_id"
    carts ||..o{ cart_items : "cart_id"
    products ||..o{ cart_items : "product_id"
```

## カート統合（キー項目）

```mermaid
erDiagram
    direction TB
    users {
        BIGINT id PK "NN / 会員ID"
        VARCHAR(128) cognito_sub UK "NN / Cognito識別子"
        VARCHAR(254) email UK "NN / メールアドレス"
    }
    carts {
        BIGINT id PK "NN / カートID"
        BIGINT user_id FK, UK "NULL可 / 会員ID"
        VARCHAR(64) guest_cart_token UK "NULL可 / ゲストカート識別子"
        BIGINT pending_merge_cart_id FK "NULL可 / 統合待ちゲストカートID"
    }
    cart_merge_logs {
        BIGINT id PK "NN / 統合履歴ID"
        BIGINT user_id FK "NN / 会員ID"
        BIGINT target_cart_id FK "NN / 統合先会員カートID"
        BIGINT source_cart_id FK, UK "NN / 統合元ゲストカートID"
    }
    carts o|..o{ carts : "pending_merge_cart_id"
    users ||..o{ cart_merge_logs : "user_id"
    carts ||..o{ cart_merge_logs : "target_cart_id"
    carts ||..|o cart_merge_logs : "source_cart_id"
```

## 注文・注文時点の記録（キー項目）

```mermaid
erDiagram
    direction TB
    users {
        BIGINT id PK "NN / 会員ID"
        VARCHAR(128) cognito_sub UK "NN / Cognito識別子"
        VARCHAR(254) email UK "NN / メールアドレス"
    }
    orders {
        BIGINT id PK "NN / 注文ID"
        VARCHAR(32) order_number UK "NN / 注文番号"
        BIGINT user_id FK "NN / 会員ID"
        VARCHAR(64) order_create_operation_id FK, UK "NN / 注文作成操作ID"
    }
    order_items {
        BIGINT id PK "NN / 注文明細ID"
        BIGINT order_id FK, UK "NN / 注文ID / 複合UKの構成列"
        INTEGER line_number UK "NN / 明細番号 / 複合UKの構成列"
        BIGINT product_id FK "NN / 商品ID"
    }
    products {
        BIGINT id PK "NN / 商品ID"
    }
    operation_histories {
        VARCHAR(64) operation_id PK "NN / 操作識別情報"
    }
    users ||..o{ orders : "user_id"
    operation_histories ||..|o orders : "order_create_operation_id"
    orders ||..o{ order_items : "order_id"
    products ||..o{ order_items : "product_id"
```

## 管理者・画像アップロード（キー項目）

```mermaid
erDiagram
    direction TB
    admins {
        BIGINT id PK "NN / 管理者ID"
        VARCHAR(128) cognito_sub UK "NN / Cognito管理者識別子"
        VARCHAR(254) email UK "NN / メールアドレス"
    }
    product_image_uploads {
        VARCHAR(64) upload_operation_id PK "NN / アップロード操作ID"
        BIGINT admin_id FK "NN / 実行管理者ID"
        VARCHAR(512) s3_object_key UK "NN / S3オブジェクトキー"
    }
    admins ||..o{ product_image_uploads : "admin_id"
```

## 全カラム版

分割図の以下のコードをMermaid対応エディタへ貼り付けると編集できます。

### 会員・保存情報・カート

```mermaid
erDiagram
    direction TB
    users {
        BIGINT id PK "NN / 会員ID"
        VARCHAR(128) cognito_sub UK "NN / Cognito識別子"
        VARCHAR(50) name "NN / 氏名"
        VARCHAR(254) email UK "NN / メールアドレス"
        VARCHAR(20) status "NN / 会員状態"
        TIMESTAMPTZ(3) created_at "NN / 登録日時"
        TIMESTAMPTZ(3) updated_at "NN / 最終更新日時"
    }
    user_saved_addresses {
        BIGINT id PK "NN / 保存配送先ID"
        BIGINT user_id FK, UK "NN / 会員ID"
        VARCHAR(50) recipient_name "NN / 受取人氏名"
        CHAR(7) postal_code "NN / 郵便番号"
        VARCHAR(10) prefecture "NN / 都道府県"
        VARCHAR(100) city_street "NN / 市区町村・番地"
        VARCHAR(100) building "NULL可 / 建物名・部屋番号"
        VARCHAR(11) phone_number "NN / 電話番号"
        BIGINT version "NN / 版情報"
        TIMESTAMPTZ(3) created_at "NN / 登録日時"
        TIMESTAMPTZ(3) updated_at "NN / 最終更新日時"
    }
    user_saved_test_cards {
        BIGINT id PK "NN / 保存テストカードID"
        BIGINT user_id FK, UK "NN / 会員ID"
        CHAR(16) card_number "NN / テストカード番号"
        CHAR(4) last4_digits "NN / 下4桁"
        VARCHAR(50) card_display_name "NN / カード表示名"
        BIGINT version "NN / 版情報"
        TIMESTAMPTZ(3) created_at "NN / 登録日時"
        TIMESTAMPTZ(3) updated_at "NN / 最終更新日時"
    }
    carts {
        BIGINT id PK "NN / カートID"
        BIGINT user_id FK, UK "NULL可 / 会員ID"
        VARCHAR(64) guest_cart_token UK "NULL可 / ゲストカート識別子"
        VARCHAR(20) status "NN / カート状態"
        BIGINT pending_merge_cart_id FK "NULL可 / 統合待ちゲストカートID"
        BIGINT version "NN / 版情報"
        TIMESTAMPTZ(3) expires_at "NULL可 / 有効期限"
        TIMESTAMPTZ(3) created_at "NN / 登録日時"
        TIMESTAMPTZ(3) updated_at "NN / 最終更新日時"
    }
    cart_items {
        BIGINT id PK "NN / カート明細ID"
        BIGINT cart_id FK, UK "NN / カートID / 複合UKの構成列"
        BIGINT product_id FK, UK "NN / 商品ID / 複合UKの構成列"
        INTEGER quantity "NN / 保存済み数量"
        VARCHAR(100) added_product_name "NN / 追加時商品名"
        INTEGER added_price_including_tax "NN / 追加時税込単価"
        TIMESTAMPTZ(3) created_at "NN / 追加日時"
        TIMESTAMPTZ(3) updated_at "NN / 最終更新日時"
    }
    products {
        BIGINT id PK "NN / 商品ID"
        VARCHAR(100) name "NN / 商品名"
        VARCHAR(200) summary "NULL可 / 商品概要"
        VARCHAR(2000) description "NULL可 / 詳細説明"
        INTEGER price_excluding_tax "NN / 税抜価格"
        SMALLINT tax_rate "NN / 消費税率(%)"
        INTEGER price_including_tax "NN / 税込価格"
        INTEGER stock_quantity "NN / 在庫数"
        VARCHAR(20) publish_status "NN / 公開状態"
        VARCHAR(20) sales_status "NN / 販売設定"
        VARCHAR(512) image_s3_key "NULL可 / 画像S3キー"
        BOOLEAN is_deleted "NN / 論理削除フラグ"
        TIMESTAMPTZ(3) deleted_at "NULL可 / 削除日時"
        BIGINT version "NN / 版情報"
        TIMESTAMPTZ(3) created_at "NN / 登録日時"
        TIMESTAMPTZ(3) updated_at "NN / 最終更新日時"
    }
    users ||..|o user_saved_addresses : "user_id"
    users ||..|o user_saved_test_cards : "user_id"
    users o|..|o carts : "user_id"
    carts ||..o{ cart_items : "cart_id"
    products ||..o{ cart_items : "product_id"
```

### カート統合

```mermaid
erDiagram
    direction TB
    users {
        BIGINT id PK "NN / 会員ID"
        VARCHAR(128) cognito_sub UK "NN / Cognito識別子"
        VARCHAR(50) name "NN / 氏名"
        VARCHAR(254) email UK "NN / メールアドレス"
        VARCHAR(20) status "NN / 会員状態"
        TIMESTAMPTZ(3) created_at "NN / 登録日時"
        TIMESTAMPTZ(3) updated_at "NN / 最終更新日時"
    }
    carts {
        BIGINT id PK "NN / カートID"
        BIGINT user_id FK, UK "NULL可 / 会員ID"
        VARCHAR(64) guest_cart_token UK "NULL可 / ゲストカート識別子"
        VARCHAR(20) status "NN / カート状態"
        BIGINT pending_merge_cart_id FK "NULL可 / 統合待ちゲストカートID"
        BIGINT version "NN / 版情報"
        TIMESTAMPTZ(3) expires_at "NULL可 / 有効期限"
        TIMESTAMPTZ(3) created_at "NN / 登録日時"
        TIMESTAMPTZ(3) updated_at "NN / 最終更新日時"
    }
    cart_merge_logs {
        BIGINT id PK "NN / 統合履歴ID"
        BIGINT user_id FK "NN / 会員ID"
        BIGINT target_cart_id FK "NN / 統合先会員カートID"
        BIGINT source_cart_id FK, UK "NN / 統合元ゲストカートID"
        VARCHAR(20) merge_status "NN / 統合状態"
        BOOLEAN has_adjustments "NN / 調整・除外有無"
        JSONB adjustments_json "NULL可 / 調整・除外明細詳細"
        BOOLEAN is_acknowledged "NN / 画面確認済みフラグ"
        TIMESTAMPTZ(3) created_at "NN / 登録日時"
        TIMESTAMPTZ(3) updated_at "NN / 最終更新日時"
    }
    carts o|..o{ carts : "pending_merge_cart_id"
    users ||..o{ cart_merge_logs : "user_id"
    carts ||..o{ cart_merge_logs : "target_cart_id"
    carts ||..|o cart_merge_logs : "source_cart_id"
```

### 注文・注文時点の記録

```mermaid
erDiagram
    direction TB
    users {
        BIGINT id PK "NN / 会員ID"
        VARCHAR(128) cognito_sub UK "NN / Cognito識別子"
        VARCHAR(50) name "NN / 氏名"
        VARCHAR(254) email UK "NN / メールアドレス"
        VARCHAR(20) status "NN / 会員状態"
        TIMESTAMPTZ(3) created_at "NN / 登録日時"
        TIMESTAMPTZ(3) updated_at "NN / 最終更新日時"
    }
    orders {
        BIGINT id PK "NN / 注文ID"
        VARCHAR(32) order_number UK "NN / 注文番号"
        BIGINT user_id FK "NN / 会員ID"
        VARCHAR(20) order_status "NN / 注文状況"
        BIGINT total_item_quantity "NN / 注文時商品数量合計"
        BIGINT subtotal_including_tax "NN / 税込商品合計"
        BIGINT shipping_fee_including_tax "NN / 税込送料"
        BIGINT payment_fee_including_tax "NN / 税込決済手数料"
        BIGINT total_amount_including_tax "NN / 合計請求金額(税込)"
        VARCHAR(50) purchaser_name "NN / 注文時・注文者氏名"
        VARCHAR(254) purchaser_email "NN / 注文時・注文者メール"
        VARCHAR(50) recipient_name "NN / 注文時・受取人氏名"
        CHAR(7) recipient_postal_code "NN / 注文時・郵便番号"
        VARCHAR(10) recipient_prefecture "NN / 注文時・都道府県"
        VARCHAR(100) recipient_city_street "NN / 注文時・市区町村番地"
        VARCHAR(100) recipient_building "NULL可 / 注文時・建物名等"
        VARCHAR(11) recipient_phone_number "NN / 注文時・電話番号"
        VARCHAR(50) payment_method_name "NN / 支払い方法表示名"
        CHAR(16) test_card_number_snapshot "NN / 注文時カード番号"
        VARCHAR(50) test_card_display_name "NN / 注文時カード表示名"
        CHAR(4) test_card_last4 "NN / 注文時カード下4桁"
        VARCHAR(20) mock_payment_status "NN / モック決済状態"
        VARCHAR(20) mock_refund_status "NN / モック返金・取消状態"
        BIGINT mock_refund_amount "NULL可 / モック返金・取消額"
        BOOLEAN save_address_requested "NN / 配送先保存希望有無"
        VARCHAR(20) save_address_result "NN / 配送先保存結果"
        BOOLEAN save_card_requested "NN / カード保存希望有無"
        VARCHAR(20) save_card_result "NN / カード保存結果"
        TIMESTAMPTZ(3) cancelled_at "NULL可 / キャンセル日時"
        VARCHAR(200) cancel_reason "NULL可 / キャンセル理由"
        BIGINT version "NN / 版情報"
        TIMESTAMPTZ(3) ordered_at "NN / 注文確定日時"
        TIMESTAMPTZ(3) updated_at "NN / 最終更新日時"
        VARCHAR(64) order_create_operation_id FK, UK "NN / 注文作成操作ID"
    }
    order_items {
        BIGINT id PK "NN / 注文明細ID"
        BIGINT order_id FK, UK "NN / 注文ID / 複合UKの構成列"
        INTEGER line_number UK "NN / 明細番号 / 複合UKの構成列"
        BIGINT product_id FK "NN / 商品ID"
        VARCHAR(100) product_name_snapshot "NN / 注文時商品名"
        INTEGER unit_price_excluding_tax_snapshot "NN / 注文時税抜単価"
        SMALLINT tax_rate_snapshot "NN / 注文時消費税率(%)"
        INTEGER unit_price_including_tax_snapshot "NN / 注文時税込単価"
        INTEGER quantity "NN / 数量"
        BIGINT subtotal_including_tax_snapshot "NN / 注文時税込明細小計"
        TIMESTAMPTZ(3) created_at "NN / 登録日時"
    }
    products {
        BIGINT id PK "NN / 商品ID"
        VARCHAR(100) name "NN / 商品名"
        VARCHAR(200) summary "NULL可 / 商品概要"
        VARCHAR(2000) description "NULL可 / 詳細説明"
        INTEGER price_excluding_tax "NN / 税抜価格"
        SMALLINT tax_rate "NN / 消費税率(%)"
        INTEGER price_including_tax "NN / 税込価格"
        INTEGER stock_quantity "NN / 在庫数"
        VARCHAR(20) publish_status "NN / 公開状態"
        VARCHAR(20) sales_status "NN / 販売設定"
        VARCHAR(512) image_s3_key "NULL可 / 画像S3キー"
        BOOLEAN is_deleted "NN / 論理削除フラグ"
        TIMESTAMPTZ(3) deleted_at "NULL可 / 削除日時"
        BIGINT version "NN / 版情報"
        TIMESTAMPTZ(3) created_at "NN / 登録日時"
        TIMESTAMPTZ(3) updated_at "NN / 最終更新日時"
    }
    operation_histories {
        VARCHAR(64) operation_id PK "NN / 操作識別情報"
        VARCHAR(40) operation_type "NN / 操作種別"
        VARCHAR(20) actor_type "NN / 実行主体区分"
        VARCHAR(64) actor_id "NN / 実行者識別子"
        BIGINT target_resource_id "NULL可 / 対象リソースID"
        VARCHAR(20) operation_status "NN / 処理状態"
        BOOLEAN is_retryable "NN / 安全な再操作可否"
        VARCHAR(50) result_code "NULL可 / 結果・エラーコード"
        JSONB result_summary_json "NULL可 / 応答要約データ"
        TIMESTAMPTZ(3) expires_at "NULL可 / 有効期限"
        TIMESTAMPTZ(3) created_at "NN / 作成日時"
        TIMESTAMPTZ(3) updated_at "NN / 最終更新日時"
        CHAR(64) request_hash "NN / 要求内容のハッシュ"
    }
    users ||..o{ orders : "user_id"
    operation_histories ||..|o orders : "order_create_operation_id"
    orders ||..o{ order_items : "order_id"
    products ||..o{ order_items : "product_id"
```

### 管理者・画像アップロード

```mermaid
erDiagram
    direction TB
    admins {
        BIGINT id PK "NN / 管理者ID"
        VARCHAR(128) cognito_sub UK "NN / Cognito管理者識別子"
        VARCHAR(254) email UK "NN / メールアドレス"
        VARCHAR(50) display_name "NN / 表示名"
        VARCHAR(30) role "NN / ロール区分"
        VARCHAR(20) status "NN / 管理者状態"
        TIMESTAMPTZ(3) created_at "NN / 登録日時"
        TIMESTAMPTZ(3) updated_at "NN / 最終更新日時"
    }
    product_image_uploads {
        VARCHAR(64) upload_operation_id PK "NN / アップロード操作ID"
        BIGINT admin_id FK "NN / 実行管理者ID"
        VARCHAR(512) s3_object_key UK "NN / S3オブジェクトキー"
        VARCHAR(255) original_filename "NN / 元ファイル名"
        VARCHAR(50) mime_type "NN / MIMEタイプ"
        INTEGER file_size "NN / ファイルサイズ"
        VARCHAR(20) status "NN / 状態"
        TIMESTAMPTZ(3) created_at "NN / 登録日時"
        TIMESTAMPTZ(3) updated_at "NN / 最終更新日時"
    }
    admins ||..o{ product_image_uploads : "admin_id"
```

## 後続設計で確定する事項

ゲスト期限7日の起点、税の端数処理、在庫引当の同期／非同期方式、モック決済の詳細状態、Cookieセッションの保存先は元資料の確認事項に従う。未確定のテーブル・FKは本図へ追加していない。
