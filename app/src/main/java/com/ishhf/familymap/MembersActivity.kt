package com.ishhf.familymap

import android.content.Intent
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.google.firebase.firestore.FirebaseFirestore

class MembersActivity : AppCompatActivity() {

    private val db = FirebaseFirestore.getInstance()
    private val members = mutableListOf<Pair<String, String>>() // uid to name

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_members)

        val prefs = getSharedPreferences("family_map_prefs", MODE_PRIVATE)
        val myUsername = prefs.getString("my_username", null)

        val recyclerView = findViewById<RecyclerView>(R.id.membersRecyclerView)
        recyclerView.layoutManager = LinearLayoutManager(this)
        val adapter = MembersAdapter(members) { uid, name ->
            val intent = Intent(this, ChatActivity::class.java)
            intent.putExtra(ChatActivity.EXTRA_OTHER_UID, uid)
            intent.putExtra(ChatActivity.EXTRA_OTHER_NAME, name)
            startActivity(intent)
        }
        recyclerView.adapter = adapter

        db.collection("users").addSnapshotListener { snapshots, error ->
            if (error != null || snapshots == null) return@addSnapshotListener
            members.clear()
            for (doc in snapshots.documents) {
                if (doc.id == myUsername) continue
                val name = doc.getString("name") ?: "بدون اسم"
                members.add(doc.id to name)
            }
            adapter.notifyDataSetChanged()
        }
    }
}

class MembersAdapter(
    private val items: List<Pair<String, String>>,
    private val onClick: (String, String) -> Unit
) : RecyclerView.Adapter<MembersAdapter.MemberViewHolder>() {

    class MemberViewHolder(view: View) : RecyclerView.ViewHolder(view) {
        val name: TextView = view.findViewById(R.id.memberName)
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): MemberViewHolder {
        val view = LayoutInflater.from(parent.context).inflate(R.layout.item_member, parent, false)
        return MemberViewHolder(view)
    }

    override fun onBindViewHolder(holder: MemberViewHolder, position: Int) {
        val (uid, name) = items[position]
        holder.name.text = if (uid == Constants.OWNER_USERNAME) "👑 $name" else name
        holder.itemView.setOnClickListener { onClick(uid, name) }
    }

    override fun getItemCount(): Int = items.size
}
