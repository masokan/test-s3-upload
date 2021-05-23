The test demonstrates a potential problem when data is uploaded using
AsyncRequestBody.fromPublisher() method.

An exception is thrown after the publisher finishes publishing data to be
uploaded. Specifically, after calling the onComplete() method of the
subscriber, the exception is thrown.  Please see the file log.error for
relevant messages.

Steps needed to reproduce the problem:

mvn clean package
AWS_ACCESS_KEY_ID=<accessKey> AWS_SECRET_ACCESS_KEY=<secret> java -jar target/s3-upload-test-0.1.jar <bucket> <objectKey>

where accessKey, secret, bucket, and objectKey are parameters that suit
your AWS account
