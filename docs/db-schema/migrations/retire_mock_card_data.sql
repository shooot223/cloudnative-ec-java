-- 適用先：PostgreSQL。旧カード保持版からの移行用。新設DBには旧列を作らない。
-- 前提：旧APIの書込み／読取りを停止し、バックアップ・復元手順・依存ビューを確認済み。
-- CASCADEなし。依存が残れば全体をロールバックして先に参照を修正する。
BEGIN;
SET LOCAL lock_timeout = '5s';
ALTER TABLE orders
  DROP COLUMN IF EXISTS test_card_number_snapshot,
  DROP COLUMN IF EXISTS test_card_display_name,
  DROP COLUMN IF EXISTS test_card_last4,
  DROP COLUMN IF EXISTS save_card_requested,
  DROP COLUMN IF EXISTS save_card_result;
DROP TABLE IF EXISTS user_saved_test_cards;
COMMIT;
-- COMMIT前の失敗はROLLBACK。COMMIT後の障害は事前バックアップを隔離環境に復元して検証する。
-- 下4桁等をダミー値で作り直さない。配送先・決済区分／状態・返金状態は対象外。
