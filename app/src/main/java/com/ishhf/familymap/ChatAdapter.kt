package com.ishhf.familymap

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView
import com.bumptech.glide.Glide
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

class ChatAdapter(private val messages: MutableList<ChatMessage>) :
    RecyclerView.Adapter<ChatAdapter.MessageViewHolder>() {

    class MessageViewHolder(view: View) : RecyclerView.ViewHolder(view) {
        val sender: TextView = view.findViewById(R.id.messageSender)
        val text: TextView = view.findViewById(R.id.messageText)
        val time: TextView = view.findViewById(R.id.messageTime)
        val image: ImageView = view.findViewById(R.id.messageImage)
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): MessageViewHolder {
        val view = LayoutInflater.from(parent.context).inflate(R.layout.item_chat_message, parent, false)
        return MessageViewHolder(view)
    }

    override fun onBindViewHolder(holder: MessageViewHolder, position: Int) {
        val msg = messages[position]
        val isOwner = msg.senderUid == Constants.OWNER_USERNAME
        holder.sender.text = if (isOwner) "👑 ${msg.senderName}" else msg.senderName

        val decryptedText = if (msg.text.isNotEmpty()) CryptoUtils.decrypt(msg.text) else ""
        holder.text.text = decryptedText
        holder.text.visibility = if (decryptedText.isEmpty()) View.GONE else View.VISIBLE

        if (msg.imageUrl.isNotEmpty()) {
            holder.image.visibility = View.VISIBLE
            Glide.with(holder.image.context).load(msg.imageUrl).into(holder.image)
        } else {
            holder.image.visibility = View.GONE
        }

        val fmt = SimpleDateFormat("HH:mm", Locale.getDefault())
        holder.time.text = fmt.format(Date(msg.timestamp))
    }

    override fun getItemCount(): Int = messages.size
}
