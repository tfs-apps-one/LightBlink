package tfsapps.lightblink;

import androidx.appcompat.app.AppCompatActivity;

//DB関連
import android.content.ContentValues;
import android.content.Context;
import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;

import android.os.Bundle;
import android.view.View;
import android.widget.ImageButton;
import android.widget.ImageView;
import android.widget.SeekBar;
import android.widget.TextView;
import android.widget.Toast;

//国設定
import java.util.Locale;

//スレッド関連
import static java.lang.Thread.sleep;


public class MainActivity extends AppCompatActivity {

    //  DB関連
    private MyOpenHelper helper;        //DBアクセス
    private int db_isopen = 0;          //DB使用したか
    private int db_interval = 0;        //DB点滅間隔
    private int db_brightness = 0;      //DB輝度調整

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

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        //  国設定
        _local = Locale.getDefault();
        _language = _local.getLanguage();
        _country = _local.getCountry();

        //  シークバーの選択
        seekSelect();
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
        /*
        if (mCameraManager != null) {
            mCameraManager = null;
        }
         */
    }

    /* **************************************************
        表示処理
    ****************************************************/
    public void screen_display(){

        /* SEEK */
        if (seek_blinkinterval == null) {
            seek_blinkinterval = (SeekBar) findViewById(R.id.seek_blink);
        }
        seek_blinkinterval.setProgress(db_interval);

        if (seek_brightness == null) {
            seek_brightness = (SeekBar) findViewById(R.id.seek_brightness);
        }
        seek_brightness.setProgress(db_brightness);

        /* IMAGE BUTTON */
        if (img_onoff == null){
            img_onoff = (ImageButton) findViewById(R.id.btn_img_onoff);
        }
        if (img_blink == null){
            img_blink = (ImageButton) findViewById(R.id.btn_img_blink);
        }
        if (img_brightness == null){
            img_brightness = (ImageButton) findViewById(R.id.btn_img_brightness);
        }

        /* ON時 */
        if (isStart){
            img_onoff.setImageResource(R.drawable.on_2);
            img_blink.setImageResource(R.drawable.blink1_off);
            img_brightness.setImageResource(R.drawable.bright1_off);
        }
        /* OFF時 */
        else {
            img_onoff.setImageResource(R.drawable.off_2);
            img_blink.setImageResource(R.drawable.blink1_on);
            img_brightness.setImageResource(R.drawable.bright1_on);
        }

        /* TEXT表示 */
        TextView text_volume1 = (TextView)findViewById(R.id.text_blink);
        text_volume1.setText(""+db_interval);
        TextView text_volume2 = (TextView)findViewById(R.id.text_brightness);
        text_volume2.setText(""+db_brightness);

    }
    /* **************************************************
        ライトスタート　ボタン処理
    ****************************************************/
    public void onStartStop(View view){

        if (isStart){
            isStart = false;
        }
        else{
            isStart = true;
        }

        screen_display();
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
    }

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
        sql.append(" FROM appinfo;");
        try {
            Cursor cursor = db.rawQuery(sql.toString(), null);
            //TextViewに表示
            StringBuilder text = new StringBuilder();
            if (cursor.moveToNext()) {
                db_isopen = cursor.getInt(0);
                db_interval = cursor.getInt(1);
                db_brightness = cursor.getInt(2);
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

            if (ret == -1) {
                Toast.makeText(this, "DataBase Create.... ERROR", Toast.LENGTH_SHORT).show();
            } else {
                Toast.makeText(this, "DataBase Create.... OK", Toast.LENGTH_SHORT).show();
            }
        } else {
            Toast.makeText(this, "Data Loading...  interval:" + db_interval, Toast.LENGTH_SHORT).show();
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
        int ret;
        try {
            ret = db.update("appinfo", insertValues, null, null);
        } finally {
            db.close();
        }
        if (ret == -1) {
            Toast.makeText(this, "Saving.... ERROR ", Toast.LENGTH_SHORT).show();
        } else {
            Toast.makeText(this, "Saving.... OK "+ "op=0:"+db_isopen+" interval=1:"+db_interval+" brightness=2:"+db_brightness, Toast.LENGTH_SHORT).show();
        }
    }
}