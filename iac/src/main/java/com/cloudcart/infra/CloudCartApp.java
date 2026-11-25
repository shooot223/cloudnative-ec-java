package com.cloudcart.infra;

import software.amazon.awscdk.App;
import software.amazon.awscdk.StackProps;

/**
 * CloudCartのAWSインフラを定義するCDKアプリのエントリーポイント。
 */
public class CloudCartApp {

    public static void main(String[] args) {
        App app = new App();

        // このアプリで管理するストレージ用Stackを登録する。
        // S3の具体的な設定はCloudCartStorageStackに任せる。
        new CloudCartStorageStack(
                app,
                "CloudCartStorageStack",
                StackProps.builder().build());

        // Javaで定義した構成からCloudFormationテンプレートを生成する。
        // この行でAWS上のバケットが作成・変更されるわけではない。
        app.synth();
    }
}