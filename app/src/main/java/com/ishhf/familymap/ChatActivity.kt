package com.ishhf.familymap

import android.Manifest
import android.content.SharedPreferences
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.media.MediaRecorder
import android.net.Uri
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.util.Base64
import android.view.MotionEvent
import android.view.View
import android.widget.Button
import android.widget.EditText
import android.widget.TextView
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.google.firebase.firestore.CollectionReference
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.Query
import java.io.ByteArrayOutputStream
import java.io.File

/** يشتغل كدردشة جماعية عادةً، أو كدردشة خاصة إذا انبعتله otherUid/otherName */
class ChatActivity : AppCompatActivity() {

    companion object {
        const val EXTRA_OTHER_UID = "otherUid"
        const val EXTRA_OTHER_NAME = "otherName"
        private const val MAX_RECORD_MS = 15000L
        private const val MAX_IMAGE_BASE64_CHARS = 250_000 // ~180 كيلوبايت تقريباً بعد التشفير
    }

    private val db = FirebaseFirestore.getInstance()
    private lateinit var prefs: SharedPreferences
    private val messages = mutableListOf<ChatMessage>()
    private lateinit var adapter: ChatAdapter
    private lateinit var recyclerView: RecyclerView

    private var otherUid: String? = null
    private var otherName: String? = null

    private var recorder: MediaRecorder? = null
    private var recordFile: File? = null
    private var recordStartTime: Long = 0
    private val recordHandler = Handler(Looper.getMainLooper())
    private var recordAutoStopRunnable: Runnable? = null

    private val pickImageLauncher = registerForActivityResult(ActivityResultContracts.GetContent()) { uri: Uri? ->
        if (uri != null) handlePickedImage(uri)
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_chat)
        prefs = getSharedPreferences("family_map_prefs", MODE_PRIVATE)

        otherUid = intent.getStringExtra(EXTRA_OTHER_UID)
        otherName = intent.getStringExtra(EXTRA_OTHER_NAME)

        findViewById<TextView>(R.id.chatTitle).text = otherName ?: "الدردشة الجماعية"

        val clearButton = findViewById<Button>(R.id.btnClearAll)
        val isOwner = myUsername() == Constants.OWNER_USERNAME
        clearButton.visibility = if (otherUid == null && isOwner) View.VISIBLE else View.GONE
        clearButton.setOnClickListener { clearAllMessages() }

        recyclerView = findViewById(R.id.chatRecyclerView)
        adapter = ChatAdapter(messages, myUsername())
        recyclerView.layoutManager = LinearLayoutManager(this)
        recyclerView.adapter = adapter

        val messageInput = findViewById<EditText>(R.id.messageInput)
        findViewById<Button>(R.id.btnSend).setOnClickListener {
            val text = messageInput.text.toString().trim()
            if (text.isNotEmpty()) {
                sendMessage(text = text)
                messageInput.setText("")
            }
        }

        findViewById<Button>(R.id.btnAttach).setOnClickListener {
            pickImageLauncher.launch("image/*")
        }

        val voiceButton = findViewById<Button>(R.id.btnVoice)
        voiceButton.setOnTouchListener { _, event ->
            when (event.action) {
                MotionEvent.ACTION_DOWN -> {
                    startRecording()
                    true
                }
                MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> {
                    stopRecordingAndSend()
                    true
                }
                else -> false
            }
        }

        listenToMessages()
    }

    private fun myUsername(): String? = prefs.getString("my_username", null)

    private fun chatCollection(): CollectionReference {
        val myId = myUsername() ?: ""
        val other = otherUid
        return if (other != null) {
            val chatId = if (myId < other) "${myId}_${other}" else "${other}_${myId}"
            db.collection("private_chats").document(chatId).collection("messages")
        } else {
            db.collection("family_chat")
        }
    }

    private fun sendMessage(
        text: String = "",
        imageData: String = "",
        audioData: String = "",
        audioDuration: Int = 0
    ) {
        val myId = myUsername() ?: return
        val data = hashMapOf<String, Any>(
            "senderUid" to myId,
            "senderName" to myId,
            "text" to CryptoUtils.encrypt(text),
            "imageData" to CryptoUtils.encrypt(imageData),
            "audioData" to CryptoUtils.encrypt(audioData),
            "audioDuration" to audioDuration,
            "timestamp" to System.currentTimeMillis()
        )
        val other = otherUid
        if (other != null) {
            data["participants"] = listOf(myId, other)
        }
        chatCollection().add(data)
    }

    // ===== الصور (نسخة مصغّرة بدون سيرفر تخزين) =====

    private fun handlePickedImage(uri: Uri) {
        try {
            val input = contentResolver.openInputStream(uri) ?: return
            val original = BitmapFactory.decodeStream(input)
            input.close()
            if (original == null) {
                Toast.makeText(this, "تعذر قراءة الصورة", Toast.LENGTH_SHORT).show()
                return
            }
            val maxDim = 300
            val ratio = minOf(maxDim.toFloat() / original.width, maxDim.toFloat() / original.height, 1f)
            val scaled = Bitmap.createScaledBitmap(
                original,
                (original.width * ratio).toInt().coerceAtLeast(1),
                (original.height * ratio).toInt().coerceAtLeast(1),
                true
            )
            val output = ByteArrayOutputStream()
            scaled.compress(Bitmap.CompressFormat.JPEG, 40, output)
            val bytes = output.toByteArray()
            val base64 = Base64.encodeToString(bytes, Base64.NO_WRAP)

            if (base64.length > MAX_IMAGE_BASE64_CHARS) {
                Toast.makeText(this, "الصورة كبيرة جداً حتى بعد التصغير، جرب صورة أبسط", Toast.LENGTH_LONG).show()
                return
            }
            sendMessage(imageData = base64)
        } catch (e: Exception) {
            Toast.makeText(this, "صار خطأ بمعالجة الصورة", Toast.LENGTH_SHORT).show()
        }
    }

    // ===== الرسائل الصوتية =====

    private fun startRecording() {
        val granted = ContextCompat.checkSelfPermission(
            this, Manifest.permission.RECORD_AUDIO
        ) == PackageManager.PERMISSION_GRANTED
        if (!granted) {
            ActivityCompat.requestPermissions(this, arrayOf(Manifest.permission.RECORD_AUDIO), 20)
            return
        }
        try {
            val file = File.createTempFile("rec_", ".3gp", cacheDir)
            recordFile = file
            recorder = MediaRecorder().apply {
                setAudioSource(MediaRecorder.AudioSource.MIC)
                setOutputFormat(MediaRecorder.OutputFormat.THREE_GPP)
                setAudioEncoder(MediaRecorder.AudioEncoder.AMR_NB)
                setOutputFile(file.absolutePath)
                prepare()
                start()
            }
            recordStartTime = System.currentTimeMillis()
            Toast.makeText(this, "🎤 جاري التسجيل... اترك الزر للإرسال", Toast.LENGTH_SHORT).show()

            val stopRunnable = Runnable { stopRecordingAndSend() }
            recordAutoStopRunnable = stopRunnable
            recordHandler.postDelayed(stopRunnable, MAX_RECORD_MS)
        } catch (e: Exception) {
            Toast.makeText(this, "تعذر بدء التسجيل", Toast.LENGTH_SHORT).show()
        }
    }

    private fun stopRecordingAndSend() {
        val activeRecorder = recorder ?: return
        recordAutoStopRunnable?.let { recordHandler.removeCallbacks(it) }
        recordAutoStopRunnable = null

        val durationSec = ((System.currentTimeMillis() - recordStartTime) / 1000).toInt().coerceAtLeast(1)
        try {
            activeRecorder.stop()
        } catch (e: Exception) {
            // تسجيل قصير جداً أو صار خطأ، نتجاهله
        }
        activeRecorder.release()
        recorder = null

        val file = recordFile ?: return
        recordFile = null
        try {
            val bytes = file.readBytes()
            val base64 = Base64.encodeToString(bytes, Base64.NO_WRAP)
            file.delete()
            sendMessage(audioData = base64, audioDuration = durationSec)
        } catch (e: Exception) {
            Toast.makeText(this, "تعذر إرسال الرسالة الصوتية", Toast.LENGTH_SHORT).show()
        }
    }

    // ===== عرض الرسائل =====

    private fun listenToMessages() {
        chatCollection()
            .orderBy("timestamp", Query.Direction.ASCENDING)
            .addSnapshotListener { snapshots, error ->
                if (error != null || snapshots == null) return@addSnapshotListener
                messages.clear()
                for (doc in snapshots.documents) {
                    val msg = ChatMessage(
                        senderUid = doc.getString("senderUid") ?: "",
                        senderName = doc.getString("senderName") ?: "",
                        text = doc.getString("text") ?: "",
                        imageData = doc.getString("imageData") ?: "",
                        audioData = doc.getString("audioData") ?: "",
                        audioDuration = (doc.getLong("audioDuration") ?: 0).toInt(),
                        timestamp = doc.getLong("timestamp") ?: 0
                    )
                    messages.add(msg)
                }
                adapter.notifyDataSetChanged()
                if (messages.isNotEmpty()) recyclerView.scrollToPosition(messages.size - 1)
            }
    }

    private fun clearAllMessages() {
        chatCollection().get().addOnSuccessListener { snapshots ->
            for (doc in snapshots.documents) {
                doc.reference.delete()
            }
            Toast.makeText(this, "تم مسح الدردشة", Toast.LENGTH_SHORT).show()
        }
    }
}
