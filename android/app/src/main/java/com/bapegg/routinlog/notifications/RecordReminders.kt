package com.bapegg.routinlog.notifications

import android.Manifest
import android.app.*
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import androidx.work.*
import com.bapegg.routinlog.MainActivity
import com.bapegg.routinlog.R
import com.bapegg.routinlog.steps.RoutineLogServices
import kotlinx.coroutines.CancellationException
import java.time.*
import java.util.concurrent.TimeUnit

data class ReminderSettings(val daily:Boolean=false,val weekly:Boolean=false,val hour:Int=21)
object ReminderDue {
    fun kinds(settings:ReminderSettings,now:ZonedDateTime,dailySent:String?,weeklySent:String?):List<String> {
        val date=now.toLocalDate().toString()
        return buildList {
            if(settings.daily&&now.hour>=settings.hour&&dailySent!=date)add("daily")
            if(settings.weekly&&now.dayOfWeek==DayOfWeek.MONDAY&&now.hour>=9&&weeklySent!=date)add("weekly")
        }
    }
}
class RecordReminders(context:Context) {
    private val app=context.applicationContext
    private val prefs=app.getSharedPreferences("record-reminders",Context.MODE_PRIVATE)
    private val work=WorkManager.getInstance(app)
    fun settings(owner:String)=ReminderSettings(prefs.getBoolean("$owner.daily",false),prefs.getBoolean("$owner.weekly",false),prefs.getInt("$owner.hour",21))
    fun save(owner:String,settings:ReminderSettings) {
        require(settings.hour in 6..22)
        check(prefs.edit().putBoolean("$owner.daily",settings.daily).putBoolean("$owner.weekly",settings.weekly).putInt("$owner.hour",settings.hour).commit())
        bind(owner)
    }
    fun bind(owner:String?) {
        if(owner==null){work.cancelUniqueWork("record-reminders");NotificationManagerCompat.from(app).cancelAll();return}
        val s=settings(owner)
        if(!s.daily&&!s.weekly){work.cancelUniqueWork("record-reminders");return}
        val request=PeriodicWorkRequestBuilder<RecordReminderWorker>(1,TimeUnit.HOURS)
            .setConstraints(Constraints.Builder().setRequiredNetworkType(NetworkType.CONNECTED).setRequiresBatteryNotLow(true).build()).build()
        work.enqueueUniquePeriodicWork("record-reminders",ExistingPeriodicWorkPolicy.KEEP,request)
    }
    fun erase(owner:String) {
        // Identity observation cancels scheduling on logout; deleting an old account must not cancel a new account's reminders.
        val editor=prefs.edit();prefs.all.keys.filter { it.startsWith("$owner.") }.forEach { editor.remove(it) };editor.commit()
    }
    fun enabled()=(Build.VERSION.SDK_INT<33||ContextCompat.checkSelfPermission(app,Manifest.permission.POST_NOTIFICATIONS)==PackageManager.PERMISSION_GRANTED)&&NotificationManagerCompat.from(app).areNotificationsEnabled()
    fun deliver(owner:String,zone:ZoneId) {
        if(!enabled())return
        val now=ZonedDateTime.now(zone)
        val due=ReminderDue.kinds(settings(owner),now,prefs.getString("$owner.daily.sent",null),prefs.getString("$owner.weekly.sent",null))
        val manager=app.getSystemService(NotificationManager::class.java)
        manager.createNotificationChannel(NotificationChannel("routine-records","기록 알림",NotificationManager.IMPORTANCE_DEFAULT).apply { description="선택한 기록과 주간 리포트 알림" })
        due.forEach { kind->
            val pending=PendingIntent.getActivity(app,if(kind=="daily")1 else 2,Intent(app,MainActivity::class.java).apply { flags=Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP },PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT)
            val notification=NotificationCompat.Builder(app,"routine-records").setSmallIcon(R.drawable.ic_activity)
                .setContentTitle(if(kind=="daily")"오늘의 루틴을 남겨볼까요?"else"지난주 기록을 살펴볼까요?")
                .setContentText(if(kind=="daily")"달라진 음식과 운동만 간단히 남겨보세요."else"앱에서 지난주 식사와 운동의 흐름을 확인하세요.")
                .setVisibility(NotificationCompat.VISIBILITY_PRIVATE).setContentIntent(pending).setAutoCancel(true).build()
            try { NotificationManagerCompat.from(app).notify(if(kind=="daily")101 else 102,notification)
                prefs.edit().putString("$owner.$kind.sent",now.toLocalDate().toString()).apply()
            }catch(_:SecurityException){return}
        }
    }
}
class RecordReminderWorker(context:Context,params:WorkerParameters):CoroutineWorker(context,params) {
    override suspend fun doWork():Result {
        val reminders=RecordReminders(applicationContext)
        if(!reminders.enabled())return Result.success()
        val accounts=RoutineLogServices.get(applicationContext).accounts
        return try {
            val owner=(accounts.identity.value ?: accounts.restoreSession())?.userId ?: return Result.success()
            val profile=accounts.getProfile() ?: return Result.success()
            if(accounts.identity.value?.userId==owner)reminders.deliver(owner,ZoneId.of(profile.timeZone))
            Result.success()
        }catch(e:CancellationException){throw e}catch(_:Exception){Result.retry()}
    }
}
