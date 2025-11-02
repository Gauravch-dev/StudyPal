package com.example.studypal;

import android.content.ContentValues;
import android.content.Context;
import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;
import android.database.sqlite.SQLiteOpenHelper;

public class DatabaseHelper extends SQLiteOpenHelper {

    private static final String DATABASE_NAME = "studypal.db";
    private static final int DATABASE_VERSION = 1;

    // Tables
    public static final String TABLE_USERS = "users";
    public static final String TABLE_PLANS = "plans";

    // User Columns
    public static final String COLUMN_USER_EMAIL = "email";
    public static final String COLUMN_USER_PASSWORD = "password";
    public static final String COLUMN_USER_NAME = "name";

    // Plan Columns
    public static final String COLUMN_PLAN_ID = "id";
    public static final String COLUMN_PLAN_SUBJECT = "subject";
    public static final String COLUMN_PLAN_TOPICS = "topics";
    public static final String COLUMN_PLAN_DATE = "date";
    public static final String COLUMN_PLAN_COMPLETED = "completed";

    public DatabaseHelper(Context context) {
        super(context, DATABASE_NAME, null, DATABASE_VERSION);
    }

    @Override
    public void onCreate(SQLiteDatabase db) {
        String CREATE_USERS_TABLE = "CREATE TABLE " + TABLE_USERS + "("
                + COLUMN_USER_EMAIL + " TEXT PRIMARY KEY,"
                + COLUMN_USER_PASSWORD + " TEXT,"
                + COLUMN_USER_NAME + " TEXT)";
        db.execSQL(CREATE_USERS_TABLE);

        String CREATE_PLANS_TABLE = "CREATE TABLE " + TABLE_PLANS + "("
                + COLUMN_PLAN_ID + " INTEGER PRIMARY KEY AUTOINCREMENT,"
                + COLUMN_PLAN_SUBJECT + " TEXT,"
                + COLUMN_PLAN_TOPICS + " TEXT,"
                + COLUMN_PLAN_DATE + " TEXT,"
                + COLUMN_PLAN_COMPLETED + " INTEGER,"
                + COLUMN_USER_EMAIL + " TEXT,"
                + "FOREIGN KEY(" + COLUMN_USER_EMAIL + ") REFERENCES " + TABLE_USERS + "(" + COLUMN_USER_EMAIL + "))";
        db.execSQL(CREATE_PLANS_TABLE);
    }

    @Override
    public void onUpgrade(SQLiteDatabase db, int oldVersion, int newVersion) {
        db.execSQL("DROP TABLE IF EXISTS " + TABLE_PLANS);
        db.execSQL("DROP TABLE IF EXISTS " + TABLE_USERS);
        onCreate(db);
    }

    // ---------- USER METHODS ----------
    public boolean addUser(String email, String password, String name) {
        SQLiteDatabase db = this.getWritableDatabase();
        ContentValues values = new ContentValues();
        values.put(COLUMN_USER_EMAIL, email);
        values.put(COLUMN_USER_PASSWORD, password);
        values.put(COLUMN_USER_NAME, name);

        long result = db.insert(TABLE_USERS, null, values);
        db.close();
        return result != -1;
    }

    public boolean checkUser(String email, String password) {
        SQLiteDatabase db = this.getReadableDatabase();
        Cursor cursor = db.query(TABLE_USERS,
                new String[]{COLUMN_USER_EMAIL},
                COLUMN_USER_EMAIL + "=? AND " + COLUMN_USER_PASSWORD + "=?",
                new String[]{email, password}, null, null, null);

        boolean exists = cursor.getCount() > 0;
        cursor.close();
        db.close();
        return exists;
    }

    // ---------- PLAN METHODS ----------
    public boolean addPlan(String subject, String topics, String date, String userEmail) {
        SQLiteDatabase db = this.getWritableDatabase();
        ContentValues values = new ContentValues();
        values.put(COLUMN_PLAN_SUBJECT, subject);
        values.put(COLUMN_PLAN_TOPICS, topics);
        values.put(COLUMN_PLAN_DATE, date);
        values.put(COLUMN_PLAN_COMPLETED, 0);
        values.put(COLUMN_USER_EMAIL, userEmail);

        long result = db.insert(TABLE_PLANS, null, values);
        db.close();
        return result != -1;
    }

    public void markPlanCompleted(int id, boolean isCompleted) {
        SQLiteDatabase db = this.getWritableDatabase();
        ContentValues values = new ContentValues();
        values.put(COLUMN_PLAN_COMPLETED, isCompleted ? 1 : 0);
        db.update(TABLE_PLANS, values, COLUMN_PLAN_ID + "=?", new String[]{String.valueOf(id)});
        db.close();
    }

    public Cursor getPlans(String userEmail) {
        SQLiteDatabase db = this.getReadableDatabase();
        return db.query(TABLE_PLANS, null, COLUMN_USER_EMAIL + "=?",
                new String[]{userEmail}, null, null, null);
    }
}
