package com.ishhf.familymap

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.Service
import android.content.Context
import android.content.Intent
import android.os.Build
import android.os.IBinder
import com.google.firebase.firestore.DocumentChange
import com.google.firebase.firestore.DocumentSnapshot
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.Query

/**
 * تشتغل طول ما التطبيق مفتوح بالخلفية (مو مغلق تماماً) وتنبّه المستخدم
 * بإشعار محلي لما توصل رسالة جماعية أو خاصة جديدة.
 * ما بتحتاج سيرفر إشعارات خارجي (FCM)، فمجانية بالكامل.
 */
class ChatNotificationService : Service() {

    companion object {
        private const val CHANNEL_ID = "chat_notif_channel"
        private const val FOREGROUND_ID = 3
        private var notifCounter = 1000
    }

    private val db = FirebaseFirestore.getInstance()
    private var startedListening = false
    private var groupChatFirstLoad = true
    private var privateChatFirstLoad = true

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onCreate() {
        super.onCreate()
        createChannel()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        startForeground(FOREGROUND_ID, buildNotification())
        if (!startedListening) {
            startedListening = true
            listenGroupChat()
            listenPrivateChats()
        }
        return START_STICKY
    }

    private fun createChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val mgr = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
            // ما بنحدد صوت مخصص، فبيستخدم صوت تنبيه النظام الافتراضي البسيط (مش موسيقى)
            mgr.createNotificationChannel(
                NotificationChannel(CHANNEL_ID, "إشعارات الدردشة", NotificationManager.IMPORTANCE_DEFAULT)
            )
        }
    }

    private fun buildNotification(): Notification {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            Notification.Builder(this, CHANNEL_ID)
                .setContentTitle("إشعارات الدردشة نشطة")
                .setContentText("رح توصلك إشعارات الرسائل الجديدة")
                .setSmallIcon(android.R.drawable.ic_dialog_email)
                .build()
        } else {
            @Suppress("DEPRECATION")
            Notification.Builder(this)
                .setContentTitle("إشعارات الدردشة نشطة")
                .setContentText("رح توصلك إشعارات الرسائل الجديدة")
                .setSmallIcon(android.R.drawable.ic_dialog_email)
                .build()
        }
    }

    private fun myUsername(): String? {
        val prefs = getSharedPreferences("family_map_prefs", Context.MODE_PRIVATE)
        return prefs.getString("my_username", null)
    }

    private fun bodyFor(doc: DocumentSnapshot): String {
        val encryptedAudio = doc.getString("audioData") ?: ""
        val decryptedAudio = if (encryptedAudio.isNotEmpty()) CryptoUtils.decrypt(encryptedAudio) else ""
        if (decryptedAudio.isNotEmpty()) return "🎤 رسالة صوتية"

        val encryptedImage = doc.getString("imageData") ?: ""
        val decryptedImage = if (encryptedImage.isNotEmpty()) CryptoUtils.decrypt(encryptedImage) else ""
        if (decryptedImage.isNotEmpty()) return "📷 صورة"

        val encryptedText = doc.getString("text") ?: ""
        val decryptedText = if (encryptedText.isNotEmpty()) CryptoUtils.decrypt(encryptedText) else ""
        return decryptedText.ifEmpty { "رسالة جديدة" }
    }

    private fun listenGroupChat() {
        db.collection("family_chat")
            .orderBy("timestamp", Query.Direction.ASCENDING)
            .addSnapshotListener { snapshots, error ->
                if (error != null || snapshots == null) return@addSnapshotListener
                if (groupChatFirstLoad) {
                    groupChatFirstLoad = false
                    return@addSnapshotListener
                }
                for (change in snapshots.documentChanges) {
                    if (change.type != DocumentChange.Type.ADDED) continue
                    val doc = change.document
                    val senderUid = doc.getString("senderUid") ?: continue
                    if (senderUid == myUsername()) continue
                    val senderName = doc.getString("senderName") ?: "أحد أفراد العائلة"
                    showNotification(senderName, bodyFor(doc))
                }
            }
    }

    private fun listenPrivateChats() {
        val myId = myUsername() ?: return
        db.collectionGroup("messages")
            .whereArrayContains("participants", myId)
            .orderBy("timestamp", Query.Direction.ASCENDING)
            .addSnapshotListener { snapshots, error ->
                if (error != null || snapshots == null) return@addSnapshotListener
                if (privateChatFirstLoad) {
                    privateChatFirstLoad = false
                    return@addSnapshotListener
                }
                for (change in snapshots.documentChanges) {
                    if (change.type != DocumentChange.Type.ADDED) continue
                    val doc = change.document
                    val senderUid = doc.getString("senderUid") ?: continue
                    if (senderUid == myId) continue
                    val senderName = doc.getString("senderName") ?: senderUid
                    showNotification("$senderName (خاص)", bodyFor(doc))
                }
            }
    }

    private fun showNotification(title: String, body: String) {
        val mgr = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        val builder = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            Notification.Builder(this, CHANNEL_ID)
        } else {
            @Suppress("DEPRECATION")
            Notification.Builder(this)
        }
        val notification = builder
            .setContentTitle(title)
            .setContentText(body)
            .setSmallIcon(android.R.drawable.ic_dialog_email)
            .setAutoCancel(true)
            .build()
        mgr.notify(notifCounter++, notification)
    }
}
