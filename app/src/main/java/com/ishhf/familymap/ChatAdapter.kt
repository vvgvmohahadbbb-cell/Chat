package com.ishhf.familymap

import android.graphics.BitmapFactory
import android.media.MediaPlayer
import android.util.Base64
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Button
import android.widget.ImageView
import android.widget.TextView
import android.widget.Toast
import androidx.recyclerview.widget.RecyclerView
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

class ChatAdapter(
    private val messages: MutableList<ChatMessage>,
    private val myUsername: String?
) : RecyclerView.Adapter<ChatAdapter.MessageViewHolder>() {

    class MessageViewHolder(view: View) : RecyclerView.ViewHolder(view) {
        val sender: TextView = view.findViewById(R.id.messageSender)
        val text: TextView = view.findViewById(R.id.messageText)
        val time: TextView = view.findViewById(R.id.messageTime)
        val image: ImageView = view.findViewById(R.id.messageImage)
        val audioButton: Button = view.findViewById(R.id.audioButton)
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): MessageViewHolder {
        val view = LayoutInflater.from(parent.context).inflate(R.layout.item_chat_message, parent, false)
        return MessageViewHolder(view)
    }

    override fun onBindViewHolder(holder: MessageViewHolder, position: Int) {
        val msg = messages[position]
        val isOwner = msg.senderUid == Constants.OWNER_USERNAME
        val isMe = msg.senderUid == myUsername
        holder.sender.text = (if (isOwner) "👑 " else "") + msg.senderName + (if (isMe) " (أنت)" else "")

        val decryptedText = if (msg.text.isNotEmpty()) CryptoUtils.decrypt(msg.text) else ""
        holder.text.text = decryptedText
        holder.text.visibility = if (decryptedText.isEmpty()) View.GONE else View.VISIBLE

        val decryptedImage = if (msg.imageData.isNotEmpty()) CryptoUtils.decrypt(msg.imageData) else ""
        if (decryptedImage.isNotEmpty()) {
            try {
                val bytes = Base64.decode(decryptedImage, Base64.NO_WRAP)
                val bmp = BitmapFactory.decodeByteArray(bytes, 0, bytes.size)
                holder.image.setImageBitmap(bmp)
                holder.image.visibility = View.VISIBLE
            } catch (e: Exception) {
                holder.image.visibility = View.GONE
            }
        } else {
            holder.image.visibility = View.GONE
        }

        val decryptedAudio = if (msg.audioData.isNotEmpty()) CryptoUtils.decrypt(msg.audioData) else ""
        if (decryptedAudio.isNotEmpty()) {
            holder.audioButton.visibility = View.VISIBLE
            holder.audioButton.text = "▶️ رسالة صوتية (${msg.audioDuration}ث)"
            holder.audioButton.setOnClickListener {
                playAudio(holder.itemView, decryptedAudio)
            }
        } else {
            holder.audioButton.visibility = View.GONE
        }

        val fmt = SimpleDateFormat("HH:mm", Locale.getDefault())
        holder.time.text = fmt.format(Date(msg.timestamp))
    }

    override fun getItemCount(): Int = messages.size

    private fun playAudio(view: View, base64Audio: String) {
        try {
            val bytes = Base64.decode(base64Audio, Base64.NO_WRAP)
            val tempFile = File.createTempFile("voice_", ".3gp", view.context.cacheDir)
            tempFile.writeBytes(bytes)
            val player = MediaPlayer()
            player.setDataSource(tempFile.absolutePath)
            player.setOnPreparedListener { it.start() }
            player.setOnCompletionListener {
                it.release()
                tempFile.delete()
            }
            player.prepareAsync()
        } catch (e: Exception) {
            Toast.makeText(view.context, "تعذر تشغيل الرسالة الصوتية", Toast.LENGTH_SHORT).show()
        }
    }
}
