package tfsapps.lightblink;

import androidx.appcompat.app.AppCompatActivity;

//DB関連
import android.app.AlertDialog;
import android.content.ContentValues;
import android.content.Context;
import android.content.DialogInterface;
import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;

// ライト
import android.graphics.Color;
import android.hardware.camera2.CameraAccessException;
import android.hardware.camera2.CameraManager;

import android.media.AudioManager;
import android.os.Bundle;
import android.os.Handler;
import android.view.View;
import android.widget.AdapterView;
import android.widget.ArrayAdapter;
import android.widget.Button;
import android.widget.ImageButton;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.RadioButton;
import android.widget.SeekBar;
import android.widget.Spinner;
import android.widget.Switch;
import android.widget.TextView;
import android.widget.Toast;

//アラーム関連
import android.media.MediaPlayer;

//国設定
import java.util.Locale;
import java.util.Timer;
import java.util.TimerTask;

//広告
import com.google.android.gms.ads.AdView;
import com.google.android.gms.ads.AdRequest;

public class MainActivity extends AppCompatActivity {

    //  DB関連
    public MyOpenHelper helper;        //DBアクセス
    private int db_isopen = 0;          //DB使用したか
    private int db_interval = 0;        //DB点滅間隔
    private int db_brightness = 0;      //DB輝度調整
    private int db_data1 = 0;           //DBユーザーレベル
    private int db_data2 = 0;           //DB自動ONのSW
    private int db_data3 = 0;           //DB画面タイプのSW

    //  国設定
    private Locale _local;
    private String _language;
    private String _country;

    private ImageButton img_onoff;      //ライトON/OFFボタン
    private ImageButton img_blink;      //点滅ボタン
    private ImageButton img_brightness; //輝度調整ボタン
    private SeekBar seek_blinkinterval; //点滅間隔
    private SeekBar seek_brightness;    //輝度調整
    private boolean isStart = false;
    private Switch sw_auto;             //トグルＳＷ


    //  スレッド関連
    private boolean blinking = false;
    public Timer blinkTimer;					//タイマー用
    public BlinkingTask blinkTimerTask;		//タイマタスククラス
    public Handler bHandler = new Handler();   //UI Threadへのpost用ハンドラ

    public Timer mainTimer;					//タイマー用
    public MainTimerTask mainTimerTask;		//タイマタスククラス
    public Handler mHandler = new Handler();   //UI Threadへのpost用ハンドラ

    //  ライト関連
    private CameraManager mCameraManager;
    private String mCameraId = null;
    private boolean isOn = false;

    //  点滅用のサウンド
//    private MediaPlayer bgm;
    private MediaPlayer loadsound;
    private AudioManager am;
    private int init_volume;    //アプリ起動時の音量値

    //  広告
    private AdView mAdview;

    //  ユーザーレベル最大５
    final int LV_MAX = 5;
//    private String[] spinnerItems_EN = {"Screen Type", " 1: Default", " 2: Gray", " 3: Oreange"};
//    private String[] spinnerItems_JP = {"画面タイプ", " 1:デフォルト", " 2: グレイ", " 3: オレンジ"};
//    private String[] spinnerItems = {};


    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        //  国設定
        _local = Locale.getDefault();
        _language = _local.getLanguage();
        _country = _local.getCountry();

        //  音声（むおん）
//        am = (AudioManager) getSystemService(Context.AUDIO_SERVICE);
//        init_volume = am.getStreamVolume(AudioManager.STREAM_MUSIC);

        //  広告
        mAdview = findViewById(R.id.adView);
        AdRequest adRequest = new AdRequest.Builder().build();
        mAdview.loadAd(adRequest);

        //  カメラ初期化
        mCameraManager = (CameraManager) getSystemService(Context.CAMERA_SERVICE);
        mCameraManager.registerTorchCallback(new CameraManager.TorchCallback() {
            @Override
            public void onTorchModeChanged(String cameraId, boolean enabled) {
                super.onTorchModeChanged(cameraId, enabled);
                mCameraId = cameraId;
                isOn = enabled;
            }
        }, new Handler());

        //  シークバーの選択
        seekSelect();

        //  スピナーの選択
//        spinnerSelect();
    }
    /* **************************************************
        各種OS上の動作定義
    ****************************************************/
    @Override
    public void onStart() {
        super.onStart();

        //DBのロード
        /* データベース */
        helper = new MyOpenHelper(this);
        AppDBInitRoad();
        screen_display();

        //音声初期化
        if (am == null) {
            am = (AudioManager) getSystemService(Context.AUDIO_SERVICE);
            init_volume = am.getStreamVolume(AudioManager.STREAM_MUSIC);
        }

        if (sw_auto == null) {
            sw_auto = (Switch) findViewById(R.id.sw_autostart);
        }
    }
    @Override
    public void onResume() {
        super.onResume();
    }

    @Override
    public void onPause(){
        super.onPause();
        //  DB更新
        AppDBUpdated();
    }
    @Override
    public void onStop(){
        super.onStop();
        //  DB更新
        AppDBUpdated();
    }
    @Override
    public void onDestroy(){
        super.onDestroy();
        //  DB更新
        AppDBUpdated();

        //カメラ
        if (mCameraManager != null) {
            mCameraManager = null;
        }
        /* 音量の戻しの処理 */
        if (am != null){
            am.setStreamVolume(AudioManager.STREAM_MUSIC, init_volume, 0);
            am = null;
        }
    }

    /* **************************************************
        表示処理
    ****************************************************/
    public void screen_display(){

        Button btn_tips = (Button)findViewById(R.id.btn_tips);
        RadioButton rbtn1 = (RadioButton)findViewById((R.id.rbtn_default));
        RadioButton rbtn2 = (RadioButton)findViewById((R.id.rbtn_gray));
        RadioButton rbtn3 = (RadioButton)findViewById((R.id.rbtn_orange));
        Switch sw1 = (Switch) findViewById(R.id.sw_autostart);

        /* SEEK */
        if (seek_blinkinterval == null) {
            seek_blinkinterval = (SeekBar) findViewById(R.id.seek_blink);
        }
        seek_blinkinterval.setProgress(db_interval);

        /* IMAGE BUTTON */
        if (img_onoff == null){
            img_onoff = (ImageButton) findViewById(R.id.btn_img_onoff);
        }
        if (img_blink == null){
            img_blink = (ImageButton) findViewById(R.id.btn_img_blink);
        }

        /* ON時 */
        if (isStart){
            btn_tips.setBackgroundTintList(null);
            btn_tips.setTextColor(getColor(R.color.material_on_background_disabled));
            btn_tips.setBackgroundResource(R.drawable.btn_grad3);
        }
        /* OFF時 */
        else {
            btn_tips.setBackgroundTintList(null);
            btn_tips.setTextColor(getColor(R.color.purple_700));
            btn_tips.setBackgroundResource(R.drawable.btn_grad3);
        }

        /* TEXT表示 */
        TextView text_onoff = (TextView)findViewById(R.id.text_onoff);
        if (isStart){
            text_onoff.setText("O N");
        }
        else{
            text_onoff.setText("OFF");
        }
        TextView text_volume1 = (TextView)findViewById(R.id.text_blink);
        text_volume1.setText(""+db_interval);

        TextView text_status = (TextView)findViewById(R.id.text_status);
        int data = db_interval*100;
        String s1 = getResources().getString(R.string.string_always);
        String s2 = getResources().getString(R.string.string_interval);
        String s3 = getResources().getString(R.string.string_msec);
        switch (db_interval){
            case 0:     text_status.setText(s1);
                        break;
            default:    text_status.setText(s2+" "+data+" "+s3);
                        break;
        }

        /*
        TextView text_volume2 = (TextView)findViewById(R.id.text_brightness);
        text_volume2.setText(""+db_brightness);
        */

        /* レイアウトのアクティブ表示 */
        LinearLayout lay_normal_11 = (LinearLayout)findViewById(R.id.linearLayout11);
        LinearLayout lay_normal_12 = (LinearLayout)findViewById(R.id.linearLayout12);
        LinearLayout lay_normal_13 = (LinearLayout)findViewById(R.id.linearLayout13);
        LinearLayout lay_normal_22 = (LinearLayout)findViewById(R.id.linearLayout22);

        switch (db_data2) {
            default:
            case 1:
                text_onoff.setTextColor(getColor(R.color.teal_700));
                text_status.setTextColor(getColor(R.color.teal_700));
                text_volume1.setTextColor(getColor(R.color.teal_700));
                text_status.setTextColor(getColor(R.color.teal_700));
                rbtn1.setTextColor(getColor(R.color.teal_700));
                rbtn2.setTextColor(getColor(R.color.teal_700));
                rbtn3.setTextColor(getColor(R.color.teal_700));
                sw1.setTextColor(getColor(R.color.teal_700));

                // ON
                if (isStart == true) {
                    img_onoff.setImageResource(R.drawable.on_2);

                    lay_normal_11.setBackgroundResource(R.drawable.btn_round);
                    lay_normal_12.setBackgroundResource(R.drawable.btn_grad3);
                    lay_normal_13.setBackgroundResource(R.drawable.btn_grad3);
                    lay_normal_22.setBackgroundResource(R.drawable.btn_grad3);
                }
                // OFF
                else {
                    img_onoff.setImageResource(R.drawable.off_2);

                    lay_normal_11.setBackgroundResource(R.drawable.btn_grad3);
                    lay_normal_12.setBackgroundResource(R.drawable.btn_round);
                    lay_normal_13.setBackgroundResource(R.drawable.btn_round);
                    lay_normal_22.setBackgroundResource(R.drawable.btn_round);
                }
                break;
            case 2:
                text_onoff.setTextColor(getColor(R.color.black));
                text_status.setTextColor(getColor(R.color.black));
                text_volume1.setTextColor(getColor(R.color.black));
                text_status.setTextColor(getColor(R.color.black));
                rbtn1.setTextColor(getColor(R.color.black));
                rbtn2.setTextColor(getColor(R.color.black));
                rbtn3.setTextColor(getColor(R.color.black));
                sw1.setTextColor(getColor(R.color.black));

                // ON
                if (isStart == true) {
                    img_onoff.setImageResource(R.drawable.on_3);

                    lay_normal_11.setBackgroundResource(R.drawable.btn_grad1);
                    lay_normal_12.setBackgroundResource(R.drawable.btn_grad3);
                    lay_normal_13.setBackgroundResource(R.drawable.btn_grad3);
                    lay_normal_22.setBackgroundResource(R.drawable.btn_grad3);
                }
                // OFF
                else {
                    img_onoff.setImageResource(R.drawable.off_3);

                    lay_normal_11.setBackgroundResource(R.drawable.btn_grad3);
                    lay_normal_12.setBackgroundResource(R.drawable.btn_grad1);
                    lay_normal_13.setBackgroundResource(R.drawable.btn_grad1);
                    lay_normal_22.setBackgroundResource(R.drawable.btn_grad1);
                }
                break;
            case 3:
                text_onoff.setTextColor(getColor(R.color.org_red));
                text_status.setTextColor(getColor(R.color.org_red));
                text_volume1.setTextColor(getColor(R.color.org_red));
                text_status.setTextColor(getColor(R.color.org_red));
                rbtn1.setTextColor(getColor(R.color.org_red));
                rbtn2.setTextColor(getColor(R.color.org_red));
                rbtn3.setTextColor(getColor(R.color.org_red));
                sw1.setTextColor(getColor(R.color.org_red));

                // ON
                if (isStart == true) {
                    img_onoff.setImageResource(R.drawable.on_4);

                    lay_normal_11.setBackgroundResource(R.drawable.btn_grad2);
                    lay_normal_12.setBackgroundResource(R.drawable.btn_grad3);
                    lay_normal_13.setBackgroundResource(R.drawable.btn_grad3);
                    lay_normal_22.setBackgroundResource(R.drawable.btn_grad3);
                }
                // OFF
                else {
                    img_onoff.setImageResource(R.drawable.off_4);

                    lay_normal_11.setBackgroundResource(R.drawable.btn_grad3);
                    lay_normal_12.setBackgroundResource(R.drawable.btn_grad2);
                    lay_normal_13.setBackgroundResource(R.drawable.btn_grad2);
                    lay_normal_22.setBackgroundResource(R.drawable.btn_grad2);
                }
                break;
        }
    }

    /* **************************************************
        ライトスタート　ボタン処理
    ****************************************************/
    public void onStartStop(View view){

        if (isStart){
            light_OFF();
            isStart = false;
        }
        else{
            light_ON();
            isStart = true;
        }

        screen_display();
    }


    /* **************************************************
        TIPS　ボタン処理
    ****************************************************/
    public void onTips(View view){
        AlertDialog.Builder guide = new AlertDialog.Builder(this);
        TextView vmessage = new TextView(this);
        int level = 0;
        String pop_message = "";
        String btn_yes = "";
        String btn_no = "";

        if (isStart == false) {

            //ユーザーレベル算出
            level = db_data1;
            level++;
            if (level >= LV_MAX){
                level = LV_MAX;
            }

            if (_language.equals("ja")) {

                pop_message += "\n\n 動画を視聴して操作履歴を増やしますか？" +
                        "\n\n\n ・視聴するたびに履歴数が増えます" +
                        "\n ・現在の履歴数「"+db_data1+"」→「"+level+"」"+"\n \n\n\n";

                btn_yes += "視聴";
                btn_no += "中止";
            }
            else{
                pop_message += "\n\n \n" +
                        "Do you want to watch the video and increase the operation history?" +
                        "\n\n\n The number of histories increases every time you watch." +
                        "\n\n Number of histories「"+db_data1+"」→「"+level+"」"+"\n \n\n\n";

                btn_yes += "YES";
                btn_no += "N O";
            }

            //メッセージ
            vmessage.setText(pop_message);
            vmessage.setBackgroundColor(Color.DKGRAY);
            vmessage.setTextColor(Color.WHITE);
//            vmessage.setTextSize(20);

            //タイトル
            guide.setTitle("TIPS");
            guide.setIcon(R.drawable.present);
            guide.setView(vmessage);

            guide.setPositiveButton(btn_yes, new DialogInterface.OnClickListener() {
                @Override
                public void onClick(DialogInterface dialog, int which) {
                    /*
                    if (mRewardedVideoAd.isLoaded()) {
                        mRewardedVideoAd.show();
                    }

                     */
                }
            });
            guide.setNegativeButton(btn_no, new DialogInterface.OnClickListener() {
                @Override
                public void onClick(DialogInterface dialog, int which) {
                    screen_display();
                }
            });

            guide.create();
            guide.show();
        }
        else{

        }
    }


    /* **************************************************
        シークバー　選択時の処理
    ****************************************************/
    public void seekSelect(){
        //  点滅間隔
        seek_blinkinterval = (SeekBar)findViewById(R.id.seek_blink);
        seek_blinkinterval.setOnSeekBarChangeListener(
                new SeekBar.OnSeekBarChangeListener() {
                    //ツマミをドラッグした時
                    @Override
                    public void onProgressChanged(SeekBar seekBar, int i, boolean b) {
                        if (isStart == false){
                            db_interval = seekBar.getProgress();
                        }
                        screen_display();
                    }
                    //ツマミに触れた時
                    @Override
                    public void onStartTrackingTouch(SeekBar seekBar) {
                    }
                    //ツマミを離した時
                    @Override
                    public void onStopTrackingTouch(SeekBar seekBar) {
                    }
                }
        );
        /*
        //  輝度調整
        seek_brightness = (SeekBar)findViewById(R.id.seek_brightness);
        seek_brightness.setOnSeekBarChangeListener(
                new SeekBar.OnSeekBarChangeListener() {
                    //ツマミをドラッグした時
                    @Override
                    public void onProgressChanged(SeekBar seekBar, int i, boolean b) {
                        if (isStart == false){
                            db_brightness = seekBar.getProgress();
                        }
                        screen_display();
                    }
                    //ツマミに触れた時
                    @Override
                    public void onStartTrackingTouch(SeekBar seekBar) {
                    }
                    //ツマミを離した時
                    @Override
                    public void onStopTrackingTouch(SeekBar seekBar) {
                    }
                }
        );
        */
    }

    public void screen_type(int index){

        RadioButton btn1 = (RadioButton)findViewById((R.id.rbtn_default));
        RadioButton btn2 = (RadioButton)findViewById((R.id.rbtn_gray));
        RadioButton btn3 = (RadioButton)findViewById((R.id.rbtn_orange));

        if (isStart == false){
            //  画面
            db_data2 = index;
        }
        else{
            //  操作無効
            switch (db_data2){
                default:
                case 1:
                    btn1.setChecked(true);
                    btn2.setChecked(false);
                    btn3.setChecked(false);
                    break;
                case 2:
                    btn1.setChecked(false);
                    btn2.setChecked(true);
                    btn3.setChecked(false);
                    break;
                case 3:
                    btn1.setChecked(false);
                    btn2.setChecked(false);
                    btn3.setChecked(true);
                    break;
            }
        }
        screen_display();
    }
    public void onRbtn_Green(View view){
        screen_type(1);
    }
    public void onRbtn_Gray(View view){
        screen_type(2);
    }
    public void onRbtn_Orange(View view){
        screen_type(3);
    }

/*
    public void spinnerSelect(){
        sp_screen = findViewById(R.id.sp_history);

        // リスナーを登録
        sp_screen.setOnItemSelectedListener(new AdapterView.OnItemSelectedListener() {
            //　アイテムが選択された時
            @Override
            public void onItemSelected(AdapterView parent,
                                       View view, int position, long id) {
                if (isStart == false){
                    spinner_select = position;
                }
                screen_display();
            }

            //　アイテムが選択されなかった
            public void onNothingSelected(AdapterView adapterView) {
                //
            }
        });

    }
*/


    /* **************************************************
        DB初期ロードおよび設定
    ****************************************************/
    public void AppDBInitRoad() {
        SQLiteDatabase db = helper.getReadableDatabase();
        StringBuilder sql = new StringBuilder();
        sql.append(" SELECT");
        sql.append(" isopen");
        sql.append(" ,interval");
        sql.append(" ,brightness");
        sql.append(" ,data1");
        sql.append(" ,data2");
        sql.append(" ,data3");
        sql.append(" FROM appinfo;");
        try {
            Cursor cursor = db.rawQuery(sql.toString(), null);
            //TextViewに表示
            StringBuilder text = new StringBuilder();
            if (cursor.moveToNext()) {
                db_isopen = cursor.getInt(0);
                db_interval = cursor.getInt(1);
                db_brightness = cursor.getInt(2);
                db_data1 = cursor.getInt(3);
                db_data2 = cursor.getInt(4);
                db_data3 = cursor.getInt(5);
            }
        } finally {
            db.close();
        }

        db = helper.getWritableDatabase();
        if (db_isopen == 0) {
            long ret;
            /* 新規レコード追加 */
            ContentValues insertValues = new ContentValues();
            insertValues.put("isopen", 1);
            insertValues.put("interval", 1);
            insertValues.put("brightness", 1);
            insertValues.put("data1", 0);
            insertValues.put("data2", 0);
            insertValues.put("data3", 0);
            insertValues.put("data4", 0);
            insertValues.put("data5", 0);
            insertValues.put("data6", 0);
            insertValues.put("data7", 0);
            insertValues.put("data8", 0);
            insertValues.put("data9", 0);
            insertValues.put("data10", 0);
            try {
                ret = db.insert("appinfo", null, insertValues);
            } finally {
                db.close();
            }
            db_isopen = 1;
            db_interval = 1;
            db_brightness = 1;
            /*
            if (ret == -1) {
                Toast.makeText(this, "DataBase Create.... ERROR", Toast.LENGTH_SHORT).show();
            } else {
                Toast.makeText(this, "DataBase Create.... OK", Toast.LENGTH_SHORT).show();
            }
             */
        } else {
            /*
            Toast.makeText(this, "Data Loading...  interval:" + db_interval, Toast.LENGTH_SHORT).show();
             */
        }
    }

    /* **************************************************
        DB更新
    ****************************************************/
    public void AppDBUpdated() {
        SQLiteDatabase db = helper.getWritableDatabase();
        ContentValues insertValues = new ContentValues();
        insertValues.put("isopen", db_isopen);
        insertValues.put("interval", db_interval);
        insertValues.put("brightness", db_brightness);
        insertValues.put("data1", db_data1);
        insertValues.put("data2", db_data2);
        insertValues.put("data3", db_data3);
        int ret;
        try {
            ret = db.update("appinfo", insertValues, null, null);
        } finally {
            db.close();
        }
        /*
        if (ret == -1) {
            Toast.makeText(this, "Saving.... ERROR ", Toast.LENGTH_SHORT).show();
        } else {
            Toast.makeText(this, "Saving.... OK "+ "op=0:"+db_isopen+" interval=1:"+db_interval+" brightness=2:"+db_brightness, Toast.LENGTH_SHORT).show();
        }
         */
    }

    /* **************************************************
        ライト処理
    ****************************************************/
    /*
     *   ライトＯＮ
     * */
    public void light_on_exec() {
        if(mCameraId == null){
            return;
        }
        try {
            mCameraManager.setTorchMode(mCameraId, blinking);
        } catch (CameraAccessException e) {
            //エラー処理
            e.printStackTrace();
        }
        if (am != null) {
            //  ミュート状態で鳴動（画面OFFで点滅がストップしない対策
            am.setStreamVolume(AudioManager.STREAM_MUSIC, 0, 0);
        }
    }
    public void light_ON() {

        if (db_interval == 0){
            blinking = true;    //常時点灯
            light_on_exec();
        }
        else{
            this.blinkTimer = new Timer();
            this.blinkTimerTask = new BlinkingTask();
            this.blinkTimer.schedule(blinkTimerTask, (db_interval*100), (db_interval*100));
        }

        this.loadsound = (MediaPlayer) MediaPlayer.create(this, R.raw.mumu2);
        this.mainTimer = new Timer();
        //タスククラスインスタンス生成
        this.mainTimerTask = new MainTimerTask();
        //タイマースケジュール設定＆開始
        this.mainTimer.schedule(mainTimerTask, 100, 10);
    }
    /*
     *   ライトＯＦＦ
     * */
    public void light_OFF() {
        if(mCameraId == null){
            return;
        }
        try {
            mCameraManager.setTorchMode(mCameraId, false);
        } catch (CameraAccessException e) {
            //エラー処理
            e.printStackTrace();
        }

        // スレッド停止
        if (this.blinkTimer != null) {
            this.blinkTimer.cancel();
            this.blinkTimer = null;
        }
        if (this.mainTimer != null) {
            this.mainTimer.cancel();
            this.mainTimer = null;
        }
    }

    /* **************************************************
        スレッド
    ****************************************************/

    /**
     * タイマータスク派生クラス
     * run()に定周期で処理したい内容を記述
     *
     */
    public class MainTimerTask extends TimerTask {
        @Override
        public void run() {
            //ここに定周期で実行したい処理を記述します
            mHandler.post( new Runnable() {
                public void run() {
                    if (loadsound != null) {
                        //BGMタイマー起動
                        loadsound.start();
                    }
                }
            });
        }
    }


    /**
     * タイマータスク派生クラス
     * run()に定周期で処理したい内容を記述
     *
     */
    public class BlinkingTask extends TimerTask {
        @Override
        public void run() {
            //ここに定周期で実行したい処理を記述します
            bHandler.post( new Runnable() {
                public void run() {
                    light_on_exec();
                    if (blinking){
                        blinking = false;
                    }
                    else{
                        blinking = true;
                    }
                }
            });
        }
    }
}