# 01 ネットワーク設計

## 方針

RDSのプライベート配置は確定。それ以外の数値は小規模検証向け初期案。IPv4構成とし、IPv6は初期対象外。

| 項目 | 設定案 | CDKでの対応 |
|---|---|---|
| VPC | 10.20.0.0/16 / DNS support・hostnames有効 | ec2.Vpc |
| Public-A | 10.20.0.0/24 / AZ-A | ALB、ECS、踏み台候補 |
| Public-B | 10.20.1.0/24 / AZ-B | ALB、ECS配置候補 |
| DB-A | 10.20.10.0/24 / AZ-A | PRIVATE_ISOLATED |
| DB-B | 10.20.11.0/24 / AZ-B | PRIVATE_ISOLATED |
| Internet Gateway | 1つ | VPCへ接続 |
| NAT Gateway | 0台 | natGatewaysを明示して不要作成を防止 |
| Public route | 0.0.0.0/0 → IGW | Public route table |
| DB route | VPC内localのみ | Internetへのdefault routeなし |

上記は希望CIDR。CDK Vpcの自動割り当てを使う場合、実際の割り当てが異なるためこの表を更新する。表のCIDRを厳密に固定する場合は明示的なSubnet/Route定義を使う。AZ名はアカウントごとに対応が異なるため、実際の2AZを設定・記録する。

RDS Single-AZでもDB subnet groupには2AZのサブネットを含める。これはDBを2台起動する指定ではない。ECSは両Public subnetを配置候補とするが、desiredCount=1では稼働タスクは1つ。

## Security Group

| SG | 受信許可 | 送信許可案 |
|---|---|---|
| ALB | TCP443 / CloudFront origin-facing managed prefix list | TCP8080 / ECS SG |
| ECS | TCP8080 / ALB SGのみ | TCP5432 / DB SG、TCP443 / 外部サービス |
| DB | TCP5432 / ECS SG・踏み台SGのみ | 新規外向き接続は不要 |
| 踏み台 | 受信ルールなし | TCP5432 / DB SG、TCP443 / SSM関連 |

SGはステートフル。応答通信を許可するためだけの逆方向受信ルールは追加しない。NACLは初期は標準設定を使用し、細粒度の制御はSGに集約。CloudFrontのprefix listはクォータ消費が大きいためSGルール上限を確認する。これだけで自身のDistributionからのアクセスに限定できるわけではないため、ALBで専用origin headerも検証する。

ECS・EC2がPublic subnetからAWS API・ECR等にアクセスするにはPublic IPv4が必要。外向き通信のためのPublic IPと、外部からの受信許可は別。ECSはALB以外からの受信を許可しない。

## 踏み台接続

EC2にSSM Agent・適切なInstance Profileを設定する。操作者はIAMで許可されたSSMセッションのみ開始できる。SSHの22番・DB5432番をインターネットに公開しない。EC2からRDSへのremote host port forwardingを使用し、DB接続はTLSを検証する。接続後はEC2を停止する。

SSMトンネルのDB通信内容をSession Managerログに残せるとは見なさない。開始・終了の監査とDB側の必要な監査を区別する。

## 検証

- RDSへ自宅等から直接到達しない。ECS・SSM経由の踏み台からのみ接続できる。
- ECSのPublic IPへ直接8080で接続できない。
- ALBの専用headerなしのリクエストは固定403。正規CloudFront経由は成功。
- 踏み台の受信ポートはすべて閉鎖。停止・再起動後もSSM経由で接続可能。

## 参考

- https://docs.aws.amazon.com/AmazonECS/latest/developerguide/networking-outbound.html
- https://docs.aws.amazon.com/AmazonRDS/latest/UserGuide/USER_VPC.WorkingWithRDSInstanceinaVPC.html
- https://docs.aws.amazon.com/systems-manager/latest/userguide/session-manager-working-with-sessions-start.html
- https://docs.aws.amazon.com/AmazonCloudFront/latest/DeveloperGuide/restrict-access-to-load-balancer.html
