package com.cloudcart.infra;

import software.amazon.awscdk.RemovalPolicy;
import software.amazon.awscdk.Stack;
import software.amazon.awscdk.StackProps;
import software.amazon.awscdk.services.s3.BlockPublicAccess;
import software.amazon.awscdk.services.s3.Bucket;
import software.amazon.awscdk.services.s3.BucketEncryption;
import software.amazon.awscdk.services.s3.IBucket;
import software.constructs.Construct;
import java.util.List;
import software.amazon.awscdk.services.s3.BlockedEncryptionType;
import software.amazon.awscdk.services.s3.ObjectOwnership;

/**
 * CloudCartの静的コンテンツを保存するS3バケットを管理するStack。
 *
 * <p>
 * 既存バケットを対象とするため、初回は通常のdeployではなく、
 * cdk importでこの定義とAWS上のバケットを対応付ける。
 * </p>
 */
public class CloudCartStorageStack extends Stack {

    /** AWS上にすでに存在するバケットの名前。 */
    private static final String WEB_BUCKET_NAME = "cloudcart-lab.click";

    /** 後からCloudFrontなどに渡すためのバケット参照。 */
    private final IBucket webBucket;

    /**
     * 静的コンテンツを保存するS3バケットを定義したStackを生成する。
     *
     * <p>
     * パブリックアクセスの全面ブロック、SSE-S3による暗号化、
     * およびStack削除時のバケット保持（RETAIN）を設定する。
     * </p>
     *
     * @param scope このStackを配置する親Construct（通常はCDKの{@code App}）
     * @param id    このStackを一意に識別するID
     * @param props アカウントやリージョンなどのStack設定
     */
    public CloudCartStorageStack(Construct scope, String id, StackProps props) {
        super(scope, id, props);

        this.webBucket = Bucket.Builder.create(this, "WebBucket")
                .bucketName(WEB_BUCKET_NAME)

                // S3を一般公開しない。CloudFrontへの読み取り許可は既存ポリシーで管理する。
                .blockPublicAccess(BlockPublicAccess.BLOCK_ALL)

                // ACLを無効にし、バケット所有者がすべてのオブジェクトを所有する。
                .objectOwnership(ObjectOwnership.BUCKET_OWNER_ENFORCED)

                // デフォルト暗号化はSSE-S3。KMSキーは新規作成しない。
                .encryption(BucketEncryption.S3_MANAGED)

                // 現在のバケットキー設定「有効」を表す。暗号化方式の指定とは別。
                .bucketKeyEnabled(true)

                // お客様提供キー（SSE-C）を使った新規書き込みを禁止する。
                .blockedEncryptionTypes(List.of(BlockedEncryptionType.SSE_C))

                // Stackを削除しても、既存バケットとその中のファイルを残す。
                .removalPolicy(RemovalPolicy.RETAIN)
                .build();
    }

    /**
     * CloudFrontなど、バケットを利用する側に参照を渡す。
     *
     * @return 静的コンテンツを保存するS3バケット
     */
    public IBucket getWebBucket() {
        return webBucket;
    }
}