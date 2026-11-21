package com.itskills.pcdautodial

import android.app.*
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.os.Build
import org.json.JSONArray
import org.json.JSONObject

object Rem {
    const val CH = "pcd_rem"

    private fun pi(c: Context, id: Int, o: JSONObject? = null): PendingIntent {
        val i = Intent(c, RemReceiver::class.java).putExtra("id", id)
        if (o != null) i.putExtra("n", o.optString("n")).putExtra("p", o.optString("p")).putExtra("r", o.optString("r"))
        return PendingIntent.getBroadcast(c, id, i, PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)
    }

    fun save(c: Context, j: String) {
        val p = c.getSharedPreferences("rem", 0)
        val am = c.getSystemService(Context.ALARM_SERVICE) as AlarmManager
        try {
            val old = JSONArray(p.getString("j", "[]"))
            for (i in 0 until old.length()) am.cancel(pi(c, old.getJSONObject(i).getInt("id")))
        } catch (e: Exception) {}
        p.edit().putString("j", j).apply()
        scheduleAll(c, j)
    }

    fun scheduleAll(c: Context, j: String) {
        val am = c.getSystemService(Context.ALARM_SERVICE) as AlarmManager
        try {
            val a = JSONArray(j)
            for (i in 0 until a.length()) {
                val o = a.getJSONObject(i)
                val t = maxOf(o.getLong("t"), System.currentTimeMillis() + 3000)
                val p = pi(c, o.getInt("id"), o)
                if (Build.VERSION.SDK_INT >= 31 && !am.canScheduleExactAlarms()) am.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, t, p)
                else am.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, t, p)
            }
        } catch (e: Exception) {}
    }

    fun drop(c: Context, id: Int) {
        val p = c.getSharedPreferences("rem", 0)
        try {
            val a = JSONArray(p.getString("j", "[]"))
            val n = JSONArray()
            for (i in 0 until a.length()) if (a.getJSONObject(i).getInt("id") != id) n.put(a.getJSONObject(i))
            p.edit().putString("j", n.toString()).apply()
        } catch (e: Exception) {}
    }
}

class RemReceiver : BroadcastReceiver() {
    override fun onReceive(c: Context, i: Intent) {
        val nm = c.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        if (Build.VERSION.SDK_INT >= 26) nm.createNotificationChannel(NotificationChannel(Rem.CH, "Reminders", NotificationManager.IMPORTANCE_HIGH))
        val open = PendingIntent.getActivity(c, 0, Intent(c, MainActivity::class.java), PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT)
        val b = if (Build.VERSION.SDK_INT >= 26) Notification.Builder(c, Rem.CH) else Notification.Builder(c)
        b.setSmallIcon(android.R.drawable.sym_action_call)
            .setContentTitle("Call karo: " + i.getStringExtra("n"))
            .setContentText(i.getStringExtra("p") + " - " + i.getStringExtra("r"))
            .setContentIntent(open).setAutoCancel(true)
        val id = i.getIntExtra("id", 0)
        nm.notify(id, b.build())
        Rem.drop(c, id)
    }
}

class BootReceiver : BroadcastReceiver() {
    override fun onReceive(c: Context, i: Intent) {
        Rem.scheduleAll(c, c.getSharedPreferences("rem", 0).getString("j", "[]") ?: "[]")
    }
}
