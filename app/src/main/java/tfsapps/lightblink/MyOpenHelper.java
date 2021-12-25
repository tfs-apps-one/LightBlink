package tfsapps.lightblink;

import android.content.Context;
import android.database.DatabaseUtils;
import android.database.sqlite.SQLiteDatabase;
import android.database.sqlite.SQLiteOpenHelper;

public class MyOpenHelper extends SQLiteOpenHelper
{
    private static final String TABLE = "appinfo";
    public MyOpenHelper(Context context) {
        super(context, "AppDB", null, 1);
    }
    @Override
    public void onCreate(SQLiteDatabase db) {
        db.execSQL("create table " + TABLE + "("
                + "isopen integer,"             //DBオープン
                + "interval integer,"           //間隔     連続再生時
                + "brightness integer,"         //明るさ   輝度調整
                + "data1 integer,"
                + "data2 integer,"
                + "data3 integer,"
                + "data4 integer,"
                + "data5 integer,"
                + "data6 integer,"
                + "data7 integer,"
                + "data8 integer,"
                + "data9 integer,"
                + "data10 integer);");
    }
    @Override
    public void onUpgrade(SQLiteDatabase db, int oldVersion, int newVersion) {
    }
}