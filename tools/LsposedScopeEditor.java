package dev.hyperears.tools;

import android.content.ContentValues;
import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;

/**
 * One-shot deployment helper for editing an already-enabled LSPosed module scope.
 *
 * Run through root app_process so Android's own SQLite implementation coordinates with
 * the live LSPosed database instead of replacing database/WAL files.
 */
public final class LsposedScopeEditor {
    private LsposedScopeEditor() {}

    public static void main(String[] args) {
        if (args.length != 5) {
            throw new IllegalArgumentException(
                    "usage: <list|add|remove> <database> <module-package> <scope-package|*> <user-id>");
        }

        SQLiteDatabase database = SQLiteDatabase.openDatabase(
                args[1],
                null,
                SQLiteDatabase.OPEN_READWRITE);
        try {
            String operation = args[0];
            String modulePackage = args[2];
            String scopePackage = args[3];
            int userId = Integer.parseInt(args[4]);
            if ("list".equals(operation)) {
                try (Cursor cursor = database.query(
                        "scope",
                        new String[] {"app_pkg_name"},
                        "module_pkg_name = ? AND user_id = ?",
                        new String[] {modulePackage, Integer.toString(userId)},
                        null,
                        null,
                        "app_pkg_name")) {
                    while (cursor.moveToNext()) {
                        System.out.println(cursor.getString(0));
                    }
                }
            } else if ("add".equals(operation)) {
                ContentValues values = new ContentValues();
                values.put("module_pkg_name", modulePackage);
                values.put("app_pkg_name", scopePackage);
                values.put("user_id", userId);
                database.insertWithOnConflict(
                        "scope",
                        null,
                        values,
                        SQLiteDatabase.CONFLICT_IGNORE);
            } else if ("remove".equals(operation)) {
                database.delete(
                        "scope",
                        "module_pkg_name = ? AND app_pkg_name = ? AND user_id = ?",
                        new String[] {
                            modulePackage,
                            scopePackage,
                            Integer.toString(userId),
                        });
            } else {
                throw new IllegalArgumentException("unknown operation: " + operation);
            }
        } finally {
            database.close();
        }
    }
}
