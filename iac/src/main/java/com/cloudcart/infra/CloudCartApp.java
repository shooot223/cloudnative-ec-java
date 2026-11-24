package com.cloudcart.infra;

import software.amazon.awscdk.App;
import software.amazon.awscdk.CfnOutput;
import software.amazon.awscdk.Stack;
import software.amazon.awscdk.services.s3.Bucket;
import software.amazon.awscdk.services.s3.IBucket;

public final class CloudCartApp {
    public static void main(String[] args) {
        App app = new App();
        Stack stack = new Stack(app, "CloudCartStorageStack");
        // 参照のみ。バケット新規作成、ポリシー変更、CloudFormationへの取り込みは行わない。
        IBucket web = Bucket.fromBucketName(stack, "ExistingWebBucket", "cloudcart-lab.click");
        CfnOutput.Builder.create(stack, "WebBucketName").value(web.getBucketName()).build();
        app.synth();
    }
}
