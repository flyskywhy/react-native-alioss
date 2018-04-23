import {
    NativeModules,
    DeviceEventEmitter
} from 'react-native';

const AliOSSModule = NativeModules.AliyunOSS;
const resumableUploadProgressEvent = 'resumableUploadProgress';
const listeners = {};

export default class AliOSS {

    /*初始化ossclient，
    **通过AccessKey和SecretKey
    *
    */
    static initWithKey(conf, EndPoint) {
        return AliOSSModule.initAliOSSWithKey(conf.AccessKey, conf.SecretKey, conf.SecretToken, EndPoint);
    }
    /*初始化ossclient，
    **通过签名字符串，此处采用的是服务端签名
    *
    */
    static initWithSigner(AccessKey, Signature, EndPoint) {
        return AliOSSModule.initAliOSSWithKey(AccessKey, Signature, EndPoint);
    }

    static getBucketFiles(bucketName, file, maxkeys, marker) {
        return AliOSSModule.getBucketFiles(bucketName, file, maxkeys, marker);
    }

    static checkObjectExist(bucketName, objectKey) {
        return AliOSSModule.checkObjectExist(bucketName, objectKey);
    }

    static resumableUpload(conf, needCallBack, callbackUrl) {
        return AliOSSModule.resumableUpload(
            conf.bucketName,
            conf.sourceFile,
            conf.ossFile,
            needCallBack,
            callbackUrl);
    }

    static addResumableUploadProgressListener(cb) {
        listeners[cb] = DeviceEventEmitter.addListener(resumableUploadProgressEvent,
            (message) => {
                cb(message);
            });
    }

    static removeResumableUploadProgressListener(cb) {
        if (!listeners[cb]) {
            return;
        }
        listeners[cb].remove();
        listeners[cb] = null;
    }

    static cancleResumableTask() {
        AliOSSModule.cancleResumableTask();
    }

    static deleteFile(bucketName, ossFile) {
        return AliOSSModule.deleteFile(bucketName, ossFile);
    }
}
