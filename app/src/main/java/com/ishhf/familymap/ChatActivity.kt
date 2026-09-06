package com.ishhf.familymap

import android.net.Uri
import android.content.SharedPreferences
import android.os.Bundle
import android.view.View
import android.widget.Button
import android.widget.EditText
import android.widget.TextView
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.google.firebase.firestore.CollectionReference
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.Query
import com.google.firebase.storage.FirebaseStorage
import java.util.UUID

/** يشتغل كدردشة جماعية عادةً، أو كدردشة خاصة إذا انبعتله otherUid/otherName */
class ChatActivity : AppCompatActivity() {

    companion object {
        const val EXTRA_OTHER_UID = "otherUid"
        const val EXTRA_OTHER_NAME = "otherName"
    }

    private val db = FirebaseFirestore.getInstance()
    private lateinit var prefs: SharedPreferences
    private val messages = mutableListOf<ChatMessage>()
    private lateinit var adapter: ChatAdapter
    private lateinit var recyclerView: RecyclerView

    private var otherUid: String? = null
    private var otherName: String? = null

    private val pickImageLauncher = registerForActivityResult(ActivityResultContracts.GetContent()) { uri: Uri? ->
        if (uri != null) uploadImage(uri)
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
        adapter = ChatAdapter(messages)
        recyclerView.layoutManager = LinearLayoutManager(this)
        recyclerView.adapter = adapter

        val messageInput = findViewById<EditText>(R.id.messageInput)
        findViewById<Button>(R.id.btnSend).setOnClickListener {
            val text = messageInput.text.toString().trim()
            if (text.isNotEmpty()) {
                sendMessage(text, "")
                messageInput.setText("")
            }
        }

        findViewById<Button>(R.id.btnAttach).setOnClickListener {
            pickImageLauncher.launch("image/*")
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

    private fun sendMessage(text: String, imageUrl: String) {
        val myId = myUsername() ?: return
        val data = hashMapOf(
            "senderUid" to myId,
            "senderName" to myId,
            "text" to CryptoUtils.encrypt(text),
            "imageUrl" to imageUrl,
            "timestamp" to System.currentTimeMillis()
        )
        chatCollection().add(data)
    }

    private fun uploadImage(uri: Uri) {
        val myId = myUsername() ?: return
        val ref = FirebaseStorage.getInstance().reference
            .child("chat_images/${myId}_${UUID.randomUUID()}.jpg")
        ref.putFile(uri)
            .addOnSuccessListener {
                ref.downloadUrl.addOnSuccessListener { downloadUri ->
                    sendMessage("", downloadUri.toString())
                }
            }
            .addOnFailureListener {
                Toast.makeText(this, "فشل رفع الصورة", Toast.LENGTH_SHORT).show()
            }
    }

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
                        imageUrl = doc.getString("imageUrl") ?: "",
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
