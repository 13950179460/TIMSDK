package com.tencent.qcloud.tim.demo.login;

import android.os.AsyncTask;
import android.text.TextUtils;

import com.tencent.qcloud.tim.demo.utils.DemoLog;
import com.tencent.qcloud.tim.uikit.TUIKit;
import com.tencent.qcloud.tim.uikit.base.IUIKitCallBack;

import org.json.JSONObject;

import java.util.concurrent.TimeUnit;

import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.Response;

/**
 * https://docs.qq.com/doc/DV29lenJjUktkckFp
 */
public class HttpTask extends AsyncTask {

    private static final String TAG = HttpTask.class.getSimpleName();

    private static final String REQUEST_URL = "https://service-c2zjvuxa-1252463788.gz.apigw.tencentcs.com/release/demoSms";

    public static final int REQUEST_GET_SMS = 1;
    public static final int REQUEST_SMS_LOGIN = 2;
    public static final int REQUEST_TOKEN_LOGIN = 3;
    public int mRequestType = REQUEST_GET_SMS;
    public RequestCallback mRequestCallback;
    public OkHttpClient mClient;

    public HttpTask(int type) {
        mRequestType = type;
        try {
            mClient = new OkHttpClient.Builder()
                    .connectTimeout(15, TimeUnit.SECONDS)
                    .readTimeout(20, TimeUnit.SECONDS)
                    .build();
        } catch (Exception e) {
            e.printStackTrace();
            DemoLog.e(TAG, "OkHttpClient error " + e.getMessage());
        }
    }

    public interface RequestCallback {
        void onError(int requestType, Object...args);
        void onSuccess(int requestType, Object...args);
    }

    public void setRequestCallback(RequestCallback cb) {
        mRequestCallback = cb;
    }

    public void getSms(String param) throws Exception{
        JSONObject dataInfo = request(param);
        if (dataInfo == null) {
            return;
        }
        String sessionId  = dataInfo.getString("sessionId");
        if (sessionId == null) {
            DemoLog.e(TAG, "dataInfo:" + dataInfo + " sessionId:" + sessionId);
            if (mRequestCallback != null) {
                mRequestCallback.onError(mRequestType, -8880);
            }
            return;
        }
        if (mRequestCallback != null) {
            mRequestCallback.onSuccess(mRequestType, sessionId);
        }
    }

    public void login(String param) throws Exception{
        JSONObject dataInfo = request(param);
        if (dataInfo == null) {
            return;
        }
        final UserInfo userInfo = UserInfo.getInstance();
        userInfo.setPhone(dataInfo.getString("phone"));
        userInfo.setToken(dataInfo.getString("token"));
        userInfo.setName(dataInfo.getString("name"));
        // 登录的业务后台存的头像也会给trtc用，忽略这里的头像
        // userInfo.setAvatar(dataInfo.getString("avatar"));
        userInfo.setUserId(dataInfo.getString("userId"));
        userInfo.setUserSig(dataInfo.getString("userSig"));
        if (TextUtils.isEmpty(userInfo.getUserId()) || TextUtils.isEmpty(userInfo.getUserSig())) {
            DemoLog.e(TAG, "userId:" + userInfo.getUserId() + " userSig:" + userInfo.getUserSig());
            if (mRequestCallback != null) {
                mRequestCallback.onError(mRequestType, -8881);
            }
            return;
        }
        TUIKit.login(userInfo.getUserId(), userInfo.getUserSig(), new IUIKitCallBack() {
            @Override
            public void onError(String module, final int code, final String desc) {
                if (mRequestCallback != null) {
                    mRequestCallback.onError(mRequestType, code);
                }
            }

            @Override
            public void onSuccess(Object data) {
                if (mRequestCallback != null) {
                    mRequestCallback.onSuccess(mRequestType, userInfo);
                }
            }
        });
    }

    private JSONObject request(String param) throws Exception{
        Request request = new Request.Builder()
                .url(REQUEST_URL + param)
                .build();
        Response response = mClient.newCall(request).execute();
        JSONObject resJson = new JSONObject(response.body().string());
        int errorCode = resJson.optInt("errorCode");
        String errorMessage = resJson.optString("errorMessage");
        DemoLog.i(TAG, "url:" + (REQUEST_URL + param) + " errorCode:" + errorCode + " errorMessage:" + errorMessage);
        JSONObject data = resJson.getJSONObject("data");
        if (!response.isSuccessful() || errorCode != 0 || data == null) {
            DemoLog.e(TAG, "data:" + data);
            if (mRequestCallback != null) {
                mRequestCallback.onError(mRequestType, errorCode, errorMessage);
            }
            return null;
        }
        return data;
    }

    @Override
    protected Object doInBackground(Object[] objects) {
        try {
            if (mRequestType == REQUEST_GET_SMS) {
                String phone = (String) objects[0];
                String param = "?method=getSms&phone=" + phone;
                getSms(param);
            } else if (mRequestType == REQUEST_SMS_LOGIN) {
                String phone = (String) objects[0];
                String code = (String) objects[1];
                String sessionId = (String) objects[2];
                String param = "?method=login&type=im&phone=" + phone + "&code=" + code + "&sessionId=" + sessionId;
                login(param);
            } else if (mRequestType == REQUEST_TOKEN_LOGIN) {
                String phone = (String) objects[0];
                String token = (String) objects[1];
                String param = "?method=login&type=im&phone=" + phone + "&token=" + token;
                login(param);
            }
        } catch (Exception e) {
            e.printStackTrace();
            if (mRequestCallback != null) {
                mRequestCallback.onError(mRequestType, -8888, e.getLocalizedMessage());
            }
            return null;
        }

        return null;
    }
}