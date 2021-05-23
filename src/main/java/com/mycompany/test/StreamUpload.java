package com.mycompany.test;

import software.amazon.awssdk.auth.credentials.DefaultCredentialsProvider;
import software.amazon.awssdk.core.async.AsyncRequestBody;
import software.amazon.awssdk.regions.Region;
import software.amazon.awssdk.services.s3.S3AsyncClient;
import software.amazon.awssdk.services.s3.model.PutObjectRequest;
import software.amazon.awssdk.services.s3.model.PutObjectResponse;

import org.reactivestreams.Publisher;
import org.reactivestreams.Subscriber;
import org.reactivestreams.Subscription;

import java.io.IOException;
import java.io.OutputStream;
import java.nio.ByteBuffer;
import java.util.Random;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.Semaphore;

public class StreamUpload implements Publisher<ByteBuffer>, Subscription,
    Runnable {
  private static final int MAX_NUM_BUFFERS = 19;
  private static final int MAX_BUFFER_SIZE = 65536;

  private Random random;
  private byte[] buffer;
  private int numBuffersRemaining;
  private long numBuffersRequested;
  private Subscriber<? super ByteBuffer> subscriber;
  private Semaphore requestSemaphore;
  private Thread publisherThread;
  private CompletableFuture future;
  private IOException ioException;

  public StreamUpload(S3AsyncClient client, String bucket, String objectKey) {
    // Generate a random number for the number of buffers to upload
    random = new Random();
    buffer = new byte[MAX_BUFFER_SIZE];
    numBuffersRemaining = Math.max(1, random.nextInt() % MAX_NUM_BUFFERS);    
System.err.println("numBuffersRemaining=" + numBuffersRemaining);
    numBuffersRequested = 0;
    subscriber = null;
    requestSemaphore = new Semaphore(0);
    // Start the thread that publishes data to be uploaded
    publisherThread = new Thread(this);
    publisherThread.start();
    // Send upload request
    PutObjectRequest request = PutObjectRequest.builder()
                               .bucket(bucket)
                               .key(objectKey).build();
    future = client.putObject(request, AsyncRequestBody.fromPublisher(this));
  }

  public void waitForCompletion() throws IOException {
    try {
      publisherThread.join();
System.err.println("Publisher thread finished");
      future.get();
    } catch(InterruptedException ie) {
      Thread.currentThread().interrupt();
    } catch(ExecutionException ee) {
      ioException = new IOException(ee);
    }
    if (ioException != null) {
      throw new IOException(ioException);
    }
  }

  @Override
  public void subscribe(Subscriber<? super ByteBuffer> s) {
System.err.println("subscribe() called");
    subscriber = s;
    s.onSubscribe(this);
  }

  @Override
  public void request(long n) {
System.err.println("request() called with " + n);
    requestSemaphore.release((int)n);
  }

  @Override
  public void cancel() {
System.err.println("cancel() called");
    if (publisherThread.isAlive()) {
      ioException = new IOException("IO canceled");
      publisherThread.interrupt();
      requestSemaphore.release(1);
    }
  }

  @Override
  public void run() {
    while(numBuffersRemaining > 0) {
      try {
        requestSemaphore.acquire(1);
      } catch(InterruptedException ie) {
        Thread.currentThread().interrupt();
      }
      if (! Thread.currentThread().interrupted()) {
        // Send a buffer
        sendABuffer();
        numBuffersRemaining--;
      }
    }
System.err.println("Calling subscriber.onComplete()");
    subscriber.onComplete();    
System.err.println("Publisher Thread: finishing...");
  }

  private void sendABuffer() {
    // Fill up the buffer with random data
    random.nextBytes(buffer);
    // Come up with a random size for current buffer to send
    int buffSize = Math.max(1, random.nextInt() % MAX_BUFFER_SIZE);
    // Publish it
    subscriber.onNext(ByteBuffer.wrap(buffer, 0, buffSize));
  }

  public static void main(String[] args) throws Exception {
    S3AsyncClient client
        = S3AsyncClient.builder().region(Region.US_EAST_1)
          .credentialsProvider(DefaultCredentialsProvider.builder().build())
          .build();
    try {
      StreamUpload su = new StreamUpload(client, args[0], args[1]);
      su.waitForCompletion();
    } finally {
      client.close();
    }
  }

}
