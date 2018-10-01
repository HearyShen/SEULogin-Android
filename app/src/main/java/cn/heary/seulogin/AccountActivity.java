package cn.heary.seulogin;

import android.accounts.Account;
import android.animation.Animator;
import android.animation.AnimatorListenerAdapter;
import android.annotation.TargetApi;
import android.content.Context;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.net.ConnectivityManager;
import android.net.NetworkInfo;
import android.net.wifi.WifiInfo;
import android.net.wifi.WifiManager;
import android.provider.SearchRecentSuggestions;
import android.support.annotation.NonNull;
import android.support.design.widget.Snackbar;
import android.support.v7.app.AppCompatActivity;
import android.app.LoaderManager.LoaderCallbacks;

import android.content.CursorLoader;
import android.content.Loader;
import android.database.Cursor;
import android.net.Uri;
import android.os.AsyncTask;

import android.os.Build;
import android.os.Bundle;
import android.provider.ContactsContract;
import android.text.TextUtils;
import android.util.Base64;
import android.view.KeyEvent;
import android.view.View;
import android.view.View.OnClickListener;
import android.view.inputmethod.EditorInfo;
import android.widget.ArrayAdapter;
import android.widget.AutoCompleteTextView;
import android.widget.Button;
import android.widget.CheckBox;
import android.widget.EditText;
import android.widget.TextView;
import android.widget.Toast;

import org.json.JSONObject;

import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.net.URLEncoder;
import java.util.ArrayList;
import java.util.List;

import static android.Manifest.permission.READ_CONTACTS;

/**
 * A login screen that offers login via email/password.
 */
public class AccountActivity extends AppCompatActivity {

    // UI references.
    private EditText mUsernameView;
    private EditText mPasswordView;
    private CheckBox mMacAuthView;
    private TextView mResultView;

    // Config
    private String username;
    private String password;
    private Boolean isMacAuth;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_account);
        // Set up the login form.
        mUsernameView = (EditText) findViewById(R.id.username);
        mPasswordView = (EditText) findViewById(R.id.password);
        mMacAuthView = (CheckBox) findViewById(R.id.isMacAuth);
        mResultView = (TextView) findViewById(R.id.result_view);

        // get SharedPreferences instance
        final SharedPreferences preferences = AccountActivity.this.getSharedPreferences("SEUlogin", Context.MODE_PRIVATE);
        mUsernameView.setText(preferences.getString("username", ""));
        mPasswordView.setText(preferences.getString("password", ""));
        mMacAuthView.setChecked(preferences.getBoolean("isMacAuth", true));


        Button mLoginBtn = (Button) findViewById(R.id.login_btn);
        mLoginBtn.setOnClickListener(new OnClickListener() {
            @Override
            public void onClick(View view) {
                username = mUsernameView.getText().toString();
                password = mPasswordView.getText().toString();
                isMacAuth = mMacAuthView.isChecked();

                // 开启子线程
                new Thread() {
                    public void run() {
                        loginByPost(username, password, isMacAuth);
                    }
                }.start();
            }
        });

        Button mLogoutBtn = (Button) findViewById(R.id.logout_btn);
        mLogoutBtn.setOnClickListener(new OnClickListener() {
            @Override
            public void onClick(View view) {
                new Thread() {
                    public void run() {
                        logoutByPost();
                    }
                }.start();
            }
        });


        /* 获取连接信息（是否有活跃连接+是否通过WIFI连接+是否连接网络） */
        ConnectivityManager connectivityManager = (ConnectivityManager) getApplicationContext().getSystemService(Context.CONNECTIVITY_SERVICE);
        NetworkInfo networkInfo = connectivityManager.getActiveNetworkInfo();

        if (networkInfo != null && networkInfo.getType() == ConnectivityManager.TYPE_WIFI && networkInfo.isConnected()) {    // 当且仅当WiFi开启，才继续检查  wifiMgr.getWifiState()==WifiManager.WIFI_STATE_ENABLED
            /* 获取SSID */
            WifiManager wifiMgr = (WifiManager) getApplicationContext().getSystemService(Context.WIFI_SERVICE);
            WifiInfo wifiInfo = wifiMgr.getConnectionInfo();
            String wifiSSID = wifiInfo.getSSID();

            if (wifiSSID.toLowerCase().contains(getString(R.string.seu))) {
                // 开启子线程
                new Thread() {
                    public void run() {
                        loginByPost(mUsernameView.getText().toString(),
                                mPasswordView.getText().toString(),
                                mMacAuthView.isChecked()
                        );
                    }
                }.start();
            }
        }
    }

    /*
     * override onPause()
     * to save the username, password and isMacAuth automatically
     * onPause() excutes when the activity stops, goes to background and loses focus
     * */
    @Override
    protected void onPause() {
        super.onPause();
        // get SharedPreferences instance
        final SharedPreferences preferences = AccountActivity.this.getSharedPreferences("SEUlogin", Context.MODE_PRIVATE);

        // Set up the login form.
        mUsernameView = (EditText) findViewById(R.id.username);
        mPasswordView = (EditText) findViewById(R.id.password);
        mMacAuthView = (CheckBox) findViewById(R.id.isMacAuth);

        SharedPreferences.Editor editor = preferences.edit();
        editor.putString("username", mUsernameView.getText().toString());
        editor.putString("password", mPasswordView.getText().toString());
        editor.putBoolean("isMacAuth", mMacAuthView.isChecked());

        editor.apply();
    }


    /**
     * POST请求操作，loginByPost通过POST请求，实现登陆功能
     *
     * @param username
     * @param password
     * @param isMacAuth
     */
    public void loginByPost(String username, String password, Boolean isMacAuth) {

        try {
            // 请求的地址
            String spec = getString(R.string.login_url);
            // 根据地址创建URL对象
            URL url = new URL(spec);
            // 根据URL对象打开链接
            HttpURLConnection urlConnection = (HttpURLConnection) url.openConnection();
            // 设置请求的方式
            urlConnection.setRequestMethod("POST");
//            // 设置请求的超时时间
//            urlConnection.setReadTimeout(5000);
//            urlConnection.setConnectTimeout(5000);

            String b64pass = Base64.encodeToString(password.getBytes(), Base64.DEFAULT);

            // 传递的数据
            String data = "username=" + URLEncoder.encode(username, "UTF-8")
                    + "&password=" + URLEncoder.encode(b64pass, "UTF-8")
                    + "&enablemacauth=" + (isMacAuth ? "1" : "0");

            urlConnection.setDoOutput(true); // 发送POST请求必须设置允许输出
            urlConnection.setDoInput(true); // 发送POST请求必须设置允许输入
            //setDoInput的默认值就是true
            //获取POST输出流
            OutputStream os = urlConnection.getOutputStream();
            os.write(data.getBytes());  // 字节流
            os.flush();     // 完整发送POST报文数据

            // 获取响应的输入流对象
            InputStream is = urlConnection.getInputStream();
            // 创建字节输出流对象
            ByteArrayOutputStream baos = new ByteArrayOutputStream();
            // 定义读取的长度
            int len = 0;
            // 定义缓冲区
            byte buffer[] = new byte[1024];
            // 按照缓冲区的大小，循环读取
            while ((len = is.read(buffer)) != -1) { // InputStream --read-> buffer(byte[])
                // 根据读取的长度写入到os对象中
                baos.write(buffer, 0, len);         // buffer(byte[]) --write-> ByteArrayOutputStream
            }
            // 返回字符串
            final String result = baos.toString();  // ByteArrayOutputStream -> String
//                final String result = new String(baos.toByteArray());

            // 释放资源
            is.close();
            baos.close();

            JSONObject jsobj = new JSONObject(result);
            int status = jsobj.getInt("status");
            final String result_print;
            if (status == 1) {
                result_print = jsobj.getString("info") + "\n"
                        + "账号：" + jsobj.getString("logout_username") + "\n"
                        + "IP：" + jsobj.getString("logout_ip") + "\n"
                        + "位置：" + jsobj.getString("logout_location") + "\n"
                        + "认证域：" + jsobj.getString("logout_domain");
            } else {
                result_print = jsobj.getString("info");
            }
            // 通过runOnUiThread方法进行修改主线程的控件内容
            AccountActivity.this.runOnUiThread(new Runnable() {
                @Override
                public void run() {
                    mResultView.setText(result_print);
                }
            });

        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    public void logoutByPost() {

        try {
            // 请求的地址
            String spec = getString(R.string.logout_url);
            // 根据地址创建URL对象
            URL url = new URL(spec);
            // 根据URL对象打开链接
            HttpURLConnection urlConnection = (HttpURLConnection) url.openConnection();
            // 设置请求的方式
            urlConnection.setRequestMethod("POST");

            urlConnection.setDoOutput(true); // 发送POST请求必须设置允许输出
            urlConnection.setDoInput(true); // 发送POST请求必须设置允许输入
            //setDoInput的默认值就是true
//
//            if (urlConnection.getResponseCode() == 200) {
            // 获取响应的输入流对象
            InputStream is = urlConnection.getInputStream();
            // 创建字节输出流对象
            ByteArrayOutputStream baos = new ByteArrayOutputStream();
            // 定义读取的长度
            int len = 0;
            // 定义缓冲区
            byte buffer[] = new byte[1024];
            // 按照缓冲区的大小，循环读取
            while ((len = is.read(buffer)) != -1) {
                // 根据读取的长度写入到os对象中
                baos.write(buffer, 0, len);
            }
            // 返回字符串
            final String result = baos.toString();

            // 释放资源
            is.close();
            baos.close();

            JSONObject jsobj = new JSONObject(result);
            int status = jsobj.getInt("status");
            final String result_print;
            if (status == 1) {
                result_print = jsobj.getString("info");
            } else {
                result_print = jsobj.getString("info");
            }

            // 通过runOnUiThread方法进行修改主线程的控件内容
            AccountActivity.this.runOnUiThread(new Runnable() {
                @Override
                public void run() {
                    mResultView.setText(result_print);
                }
            });

        } catch (Exception e) {
            e.printStackTrace();
        }
    }

}

