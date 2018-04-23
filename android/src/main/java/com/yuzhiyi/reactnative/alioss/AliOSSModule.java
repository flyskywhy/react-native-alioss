package com.yuzhiyi.reactnative.alioss;

import android.os.Environment;
import android.text.TextUtils;
import android.util.Log;

import com.alibaba.sdk.android.oss.ClientConfiguration;
import com.alibaba.sdk.android.oss.ClientException;
import com.alibaba.sdk.android.oss.OSS;
import com.alibaba.sdk.android.oss.OSSClient;
import com.alibaba.sdk.android.oss.ServiceException;
import com.alibaba.sdk.android.oss.callback.OSSCompletedCallback;
import com.alibaba.sdk.android.oss.callback.OSSProgressCallback;
import com.alibaba.sdk.android.oss.common.auth.OSSCredentialProvider;
import com.alibaba.sdk.android.oss.common.auth.OSSCustomSignerCredentialProvider;
import com.alibaba.sdk.android.oss.common.auth.OSSStsTokenCredentialProvider;
import com.alibaba.sdk.android.oss.internal.OSSAsyncTask;
import com.alibaba.sdk.android.oss.model.DeleteObjectRequest;
import com.alibaba.sdk.android.oss.model.DeleteObjectResult;
import com.alibaba.sdk.android.oss.model.GetObjectRequest;
import com.alibaba.sdk.android.oss.model.GetObjectResult;
import com.alibaba.sdk.android.oss.model.ListObjectsRequest;
import com.alibaba.sdk.android.oss.model.ListObjectsResult;
import com.alibaba.sdk.android.oss.model.ObjectMetadata;
import com.alibaba.sdk.android.oss.model.PutObjectRequest;
import com.alibaba.sdk.android.oss.model.PutObjectResult;
import com.alibaba.sdk.android.oss.model.ResumableUploadRequest;
import com.alibaba.sdk.android.oss.model.ResumableUploadResult;
import com.facebook.react.bridge.Arguments;
import com.facebook.react.bridge.Promise;
import com.facebook.react.bridge.ReactApplicationContext;
import com.facebook.react.bridge.ReactContextBaseJavaModule;
import com.facebook.react.bridge.ReactMethod;
import com.facebook.react.bridge.ReadableArray;
import com.facebook.react.bridge.WritableArray;
import com.facebook.react.bridge.WritableMap;
import com.facebook.react.modules.core.DeviceEventManagerModule;

import java.io.File;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.HashMap;

/**
 * Created by chenwenyu on 18-1-11.
 */

public class AliOSSModule extends ReactContextBaseJavaModule {

    private OSS oss;
    private OSSAsyncTask resumableTask;
    private Long progressValue = 0L;

    public AliOSSModule(ReactApplicationContext reactContext) {
        super(reactContext);
    }

    @Override
    public boolean canOverrideExistingModule() {
        return true;
    }

    @Override
    public String getName() {
        return "AliyunOSS";
    }

    @Override
    public void initialize() {
        super.initialize();
    }

    @ReactMethod
    public void initAliOSSWithKey(String accessKeyId, String accessKeySecret,
                           String securityToken, String endPoint) {
        OSSCredentialProvider credentialProvider =  new OSSStsTokenCredentialProvider(accessKeyId, accessKeySecret, securityToken);

        ClientConfiguration conf = new ClientConfiguration();
        conf.setConnectionTimeout(15 * 1000); // 连接超时，默认15秒
        conf.setSocketTimeout(15 * 1000); // socket超时，默认15秒
        conf.setMaxConcurrentRequest(5); // 最大并发请求书，默认5个
        conf.setMaxErrorRetry(2); // 失败后最大重试次数，默认2次

        oss = new OSSClient(getReactApplicationContext().getApplicationContext(), endPoint, credentialProvider, conf);

    }

    @ReactMethod
    public void initAliOSSWithSigner(final String accessKey, final String signature, String endPoint) {
        OSSCredentialProvider credentialProvider = new OSSCustomSignerCredentialProvider() {
            @Override
            public String signContent(String content) {
                return "OSS " + accessKey + ":" + signature;
            }
        };
        ClientConfiguration conf = new ClientConfiguration();
        conf.setConnectionTimeout(15 * 1000); // 连接超时，默认15秒
        conf.setSocketTimeout(15 * 1000); // socket超时，默认15秒
        conf.setMaxConcurrentRequest(5); // 最大并发请求书，默认5个
        conf.setMaxErrorRetry(2); // 失败后最大重试次数，默认2次
        oss = new OSSClient(getReactApplicationContext().getApplicationContext(), endPoint, credentialProvider, conf);
    }

    @ReactMethod
    public void uploadObjectAsync(String bucketName, String sourceFile, String ossFile, final Promise promise) {
        // 构造上传请求
        if (sourceFile != null) {
            sourceFile = sourceFile.replace("file://", "");
        }
        PutObjectRequest put = new PutObjectRequest(bucketName, ossFile, sourceFile);
        ObjectMetadata metadata = new ObjectMetadata();
        metadata.setContentType("application/octet-stream");
        put.setMetadata(metadata);

        // 异步上传时可以设置进度回调
        put.setProgressCallback(new OSSProgressCallback<PutObjectRequest>() {
            @Override
            public void onProgress(PutObjectRequest request, long currentSize, long totalSize) {
                Log.d("PutObject", "currentSize: " + currentSize + " totalSize: " + totalSize);
                String str_currentSize = Long.toString(currentSize);
                String str_totalSize = Long.toString(totalSize);
                WritableMap onProgressValueData = Arguments.createMap();
                onProgressValueData.putString("currentSize", str_currentSize);
                onProgressValueData.putString("totalSize", str_totalSize);
                getReactApplicationContext().getJSModule(DeviceEventManagerModule.RCTDeviceEventEmitter.class)
                        .emit("uploadProgress", onProgressValueData);
            }
        });

        OSSAsyncTask task = oss.asyncPutObject(put, new OSSCompletedCallback<PutObjectRequest, PutObjectResult>() {
            @Override
            public void onSuccess(PutObjectRequest request, PutObjectResult result) {
                Log.d("PutObject", "UploadSuccess");
                Log.d("ETag", result.getETag());
                Log.d("RequestId", result.getRequestId());
                promise.resolve("onSuccess");
            }

            @Override
            public void onFailure(PutObjectRequest request, ClientException clientExcepion, ServiceException serviceException) {
                // 请求异常
                if (clientExcepion != null) {
                    // 本地异常如网络异常等
                    clientExcepion.printStackTrace();
                }
                if (serviceException != null) {
                    // 服务异常
                    Log.e("ErrorCode", serviceException.getErrorCode());
                    Log.e("RequestId", serviceException.getRequestId());
                    Log.e("HostId", serviceException.getHostId());
                    Log.e("RawMessage", serviceException.getRawMessage());
                }
                promise.reject("onFail");
            }
        });
        Log.d("AliyunOSS", "OSS uploadObjectAsync ok!");
    }

    @ReactMethod
    public void downloadObjectAsync(String bucketName, String ossFile, final Promise promise) {
        // 构造下载文件请求
        GetObjectRequest get = new GetObjectRequest(bucketName, ossFile);

        OSSAsyncTask task = oss.asyncGetObject(get, new OSSCompletedCallback<GetObjectRequest, GetObjectResult>() {
            @Override
            public void onSuccess(GetObjectRequest request, GetObjectResult result) {
                // 请求成功
                Log.d("Content-Length", "" + result.getContentLength());

                InputStream inputStream = result.getObjectContent();
                long resultLength = result.getContentLength();

                byte[] buffer = new byte[2048];
                int len;

                FileOutputStream outputStream = null;
                String localImgURL = Environment.getExternalStorageDirectory().getAbsolutePath() +
                        "/ImgCache/" +
                        System.currentTimeMillis() +
                        ".jpg";
                Log.d("localImgURL", localImgURL);
                File cacheFile = new File(localImgURL);
                if (!cacheFile.exists()) {
                    cacheFile.getParentFile().mkdirs();
                    try {
                        cacheFile.createNewFile();
                    } catch (IOException e) {
                        e.printStackTrace();
                        promise.reject("onFail");
                    }
                }
                long readSize = cacheFile.length();
                try {
                    outputStream = new FileOutputStream(cacheFile, true);
                } catch (FileNotFoundException e) {
                    e.printStackTrace();
                    promise.reject("onFail");
                }
                if(resultLength == -1){
                    promise.reject("onFail");
                }

                try {
                    while ((len = inputStream.read(buffer)) != -1) {
                        // 处理下载的数据
                        try{
                            outputStream.write(buffer,0,len);
                            readSize += len;

                            String str_currentSize = Long.toString(readSize);
                            String str_totalSize = Long.toString(resultLength);
                            WritableMap onProgressValueData = Arguments.createMap();
                            onProgressValueData.putString("currentSize", str_currentSize);
                            onProgressValueData.putString("totalSize", str_totalSize);
                            getReactApplicationContext().getJSModule(DeviceEventManagerModule.RCTDeviceEventEmitter.class)
                                    .emit("downloadProgress", onProgressValueData);

                        }catch (IOException e) {
                            e.printStackTrace();
                            promise.reject("onFail");
                        }
                    }
                    outputStream.flush();
                } catch (IOException e) {
                    e.printStackTrace();
                    promise.reject("onFail");
                } finally {
                    if (outputStream != null) {
                        try {
                            outputStream.close();
                        } catch (IOException e) {
                            promise.reject("onFail");
                        }
                    }
                    if (inputStream != null) {
                        try {
                            inputStream.close();
                        } catch (IOException e) {
                            promise.reject("onFail");
                        }
                    }
                    promise.resolve(localImgURL);
                }
            }

            @Override
            public void onFailure(GetObjectRequest request, ClientException clientExcepion, ServiceException serviceException) {
                // 请求异常
                if (clientExcepion != null) {
                    // 本地异常如网络异常等
                    clientExcepion.printStackTrace();
                }
                if (serviceException != null) {
                    // 服务异常
                    Log.e("ErrorCode", serviceException.getErrorCode());
                    Log.e("RequestId", serviceException.getRequestId());
                    Log.e("HostId", serviceException.getHostId());
                    Log.e("RawMessage", serviceException.getRawMessage());
                }
                promise.reject("onFail");
            }
        });

        // task.cancel(); // 可以取消任务

        // task.waitUntilFinished(); // 如果需要等待任务完成
    }

    @ReactMethod
    public void getBucketFiles(String bucketName,String file,int maxkeys, String marker, final Promise promise) {
        ListObjectsRequest listObjects = new ListObjectsRequest(bucketName);
        // 设定前缀
        listObjects.setPrefix(file);
        listObjects.setMaxKeys(maxkeys);
        if(!TextUtils.isEmpty(marker))
            listObjects.setMarker(marker);
        // 设置成功、失败回调，发送异步罗列请求
        OSSAsyncTask task = oss.asyncListObjects(listObjects, new OSSCompletedCallback<ListObjectsRequest, ListObjectsResult>() {
            @Override
            public void onSuccess(ListObjectsRequest request, ListObjectsResult result) {
                WritableMap buckerFilesResult = Arguments.createMap();
                WritableArray buckerFileArray = Arguments.createArray();
                for (int i = 0; i < result.getObjectSummaries().size(); i++) {
                    WritableMap buckerFile = Arguments.createMap();
                    buckerFile.putString("key",result.getObjectSummaries().get(i).getKey());
                    buckerFile.putString("eTag",result.getObjectSummaries().get(i).getETag());
                    buckerFile.putString("lastModified",result.getObjectSummaries().get(i).getLastModified().toString());
                    buckerFile.putDouble("size",(double)result.getObjectSummaries().get(i).getSize());
                    buckerFileArray.pushMap(buckerFile);
                }
                buckerFilesResult.putBoolean("isTruncated", result.isTruncated());
                buckerFilesResult.putString("nextMarker",result.getNextMarker());
                buckerFilesResult.putArray("bucketFiles",buckerFileArray);
                promise.resolve(buckerFilesResult);
            }

            @Override
            public void onFailure(ListObjectsRequest request, ClientException clientExcepion, ServiceException serviceException) {
                // 请求异常
                if (clientExcepion != null) {
                    // 本地异常如网络异常等
                    clientExcepion.printStackTrace();
                }
                if (serviceException != null) {
                    // 服务异常
                    Log.e("ErrorCode", serviceException.getErrorCode());
                    Log.e("RequestId", serviceException.getRequestId());
                    Log.e("HostId", serviceException.getHostId());
                    Log.e("RawMessage", serviceException.getRawMessage());
                }
                promise.reject("onFail");
            }
        });
        task.waitUntilFinished();
    }

    // 异步断点上传，设置记录保存路径，即使任务失败，下次启动仍能继续
    @ReactMethod
    public void resumableUpload(String bucketName, String sourceFile, final String ossFile,boolean needCallBack,final String callbackUrl, final Promise promise) {

        String recordDirectory = Environment.getExternalStorageDirectory().getAbsolutePath() + "/oss_record/";

        File recordDir = new File(recordDirectory);

        // 要保证目录存在，如果不存在则主动创建
        if (!recordDir.exists()) {
            recordDir.mkdirs();
        }
        // 构造上传请求
        if (sourceFile != null) {
            sourceFile = sourceFile.replace("file://", "");
        }
        // 创建断点上传请求，参数中给出断点记录文件的保存位置，需是一个文件夹的绝对路径
        ResumableUploadRequest request = new ResumableUploadRequest(bucketName, ossFile, sourceFile, recordDirectory);
        if(needCallBack) {
            request.setCallbackParam(new HashMap<String, String>() {
                {
                    put("callbackUrl", callbackUrl);
                    put("callbackBody", "filename=" + ossFile);
                }
            });
        }
        // 设置上传过程回调
        request.setProgressCallback(new OSSProgressCallback<ResumableUploadRequest>() {
            @Override
            public void onProgress(ResumableUploadRequest request, long currentSize, long totalSize) {
                Log.d("PutObject", "currentSize: " + currentSize + " totalSize: " + totalSize);
                Long value = totalSize == 0 ? 0 : (currentSize * 100 / totalSize);
                if (progressValue != value) {
                    progressValue = value;
                    WritableMap onProgressValueData = Arguments.createMap();
                    onProgressValueData.putString("progressValue", Long.toString(progressValue));
                    getReactApplicationContext().getJSModule(DeviceEventManagerModule.RCTDeviceEventEmitter.class)
                            .emit("resumableUploadProgress", onProgressValueData);
                }
            }
        });


        resumableTask = oss.asyncResumableUpload(request, new OSSCompletedCallback<ResumableUploadRequest, ResumableUploadResult>() {
            @Override
            public void onSuccess(ResumableUploadRequest request, ResumableUploadResult result) {
                promise.resolve("onSuccess");
            }

            @Override
            public void onFailure(ResumableUploadRequest request, ClientException clientExcepion, ServiceException serviceException) {
                // 请求异常
                if (clientExcepion != null) {
                    // 本地异常如网络异常等
                    clientExcepion.printStackTrace();
                }
                if (serviceException != null) {
                    // 服务异常
                    Log.e("ErrorCode", serviceException.getErrorCode());
                    Log.e("RequestId", serviceException.getRequestId());
                    Log.e("HostId", serviceException.getHostId());
                    Log.e("RawMessage", serviceException.getRawMessage());
                }
                promise.reject("onFail");
            }
        });

        // resumableTask.waitUntilFinished(); // 这会让 JS 线程也无法响应用户操作，直到任务完成
    }

    @ReactMethod
    public void cancleResumableTask() {
        if (resumableTask != null) {
            resumableTask.cancel();
        }
    }

    private String presignConstrainedObjectURL(String bucketName,String objectKey) {
        try {
            return oss.presignConstrainedObjectURL(bucketName, objectKey, 30 * 60);
        } catch(ClientException clientExcepion) {
            return "";
        }
    }

    @ReactMethod
    public void deleteFile(String bucketName,String objectKey, final Promise promise) {
        // 创建删除请求
        DeleteObjectRequest delete = new DeleteObjectRequest(bucketName, objectKey);
        // 异步删除
        OSSAsyncTask deleteTask = oss.asyncDeleteObject(delete, new OSSCompletedCallback<DeleteObjectRequest, DeleteObjectResult>() {
            @Override
            public void onSuccess(DeleteObjectRequest request, DeleteObjectResult result) {
                Log.d("asyncCopyAndDelObject", "success!");
                promise.resolve("onSuccess");
            }

            @Override
            public void onFailure(DeleteObjectRequest request, ClientException clientExcepion, ServiceException serviceException) {
                // 请求异常
                if (clientExcepion != null) {
                    // 本地异常如网络异常等
                    clientExcepion.printStackTrace();
                }
                if (serviceException != null) {
                    // 服务异常
                    Log.e("ErrorCode", serviceException.getErrorCode());
                    Log.e("RequestId", serviceException.getRequestId());
                    Log.e("HostId", serviceException.getHostId());
                    Log.e("RawMessage", serviceException.getRawMessage());
                }
                promise.reject("onFail");
            }

        });
    }

    @ReactMethod
    public void uploadCallBack(String bucketName, String sourceFile, String ossFile, final Promise promise) {
        PutObjectRequest put = new PutObjectRequest(bucketName, ossFile, sourceFile);
        put.setCallbackParam(new HashMap<String, String>() {
            {
                put("callbackUrl", "110.75.82.106/callback");
                put("callbackHost", "oss-cn-hangzhou.aliyuncs.com");
                put("callbackBodyType", "application/json");
                put("callbackBody", "{\"mimeType\":${mimeType},\"size\":${size}}");
            }
        });
        OSSAsyncTask task = oss.asyncPutObject(put, new OSSCompletedCallback<PutObjectRequest, PutObjectResult>() {
            @Override
            public void onSuccess(PutObjectRequest request, PutObjectResult result) {
                Log.d("PutObject", "UploadSuccess");
                // 只有设置了servercallback，这个值才有数据
                String serverCallbackReturnJson = result.getServerCallbackReturnBody();
                Log.d("servercallback", serverCallbackReturnJson);
                promise.resolve("onSuccess");
            }
            @Override
            public void onFailure(PutObjectRequest request, ClientException clientExcepion, ServiceException serviceException) {
                // 异常处理
                promise.reject("onFail");
            }
        });
    }

    @ReactMethod
    public void checkObjectExist(String bucketName,String objectKey, Promise promise) {
        try {
            if (oss.doesObjectExist(bucketName, objectKey)) {
                promise.resolve(true);
            } else {
                promise.resolve(false);
            }
        } catch (ClientException e) {
            // 本地异常如网络异常等
            e.printStackTrace();
            promise.resolve(false);
        } catch (ServiceException e) {
            // 服务异常
            Log.e("ErrorCode", e.getErrorCode());
            Log.e("RequestId", e.getRequestId());
            Log.e("HostId", e.getHostId());
            Log.e("RawMessage", e.getRawMessage());
            promise.resolve(false);
        }
    }

    @ReactMethod
    public void presignConstrainedObjectURLs(String bucketName,ReadableArray objectKeys) {
        WritableArray constrainedObjectURLs = Arguments.createArray();
        for (int i = 0; i < objectKeys.size(); i++) {
            String url = presignConstrainedObjectURL(bucketName,objectKeys.getString(i));
            WritableMap objectURL = Arguments.createMap();
            objectURL.putString("objectURL",url);
            objectURL.putString("objectKey",objectKeys.getString(i));
            constrainedObjectURLs.pushMap(objectURL);
        }
        getReactApplicationContext().getJSModule(DeviceEventManagerModule.RCTDeviceEventEmitter.class)
                .emit("presignConstrainedObjectURLs", constrainedObjectURLs);
    }
}
