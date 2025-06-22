package com.example.confessionapp.ui

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.TextView
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import com.example.confessionapp.R
import com.example.confessionapp.data.models.PriestUser
// TODO: Import a library like Glide or Picasso for image loading if priest_image_view is used with URLs

class PriestAdapter(
    private val onItemClicked: (PriestUser) -> Unit
) : ListAdapter<PriestUser, PriestAdapter.PriestViewHolder>(PriestDiffCallback()) {

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): PriestViewHolder {
        val view = LayoutInflater.from(parent.context)
            .inflate(R.layout.item_priest_card, parent, false)
        return PriestViewHolder(view)
    }

    override fun onBindViewHolder(holder: PriestViewHolder, position: Int) {
        val priest = getItem(position)
        holder.bind(priest)
        holder.itemView.setOnClickListener {
            onItemClicked(priest)
        }
    }

    class PriestViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
        private val nameTextView: TextView = itemView.findViewById(R.id.priest_name_text_view)
        private val languagesTextView: TextView = itemView.findViewById(R.id.priest_languages_text_view)
        private val imageView: ImageView = itemView.findViewById(R.id.priest_image_view) // Placeholder for image loading

        fun bind(priest: PriestUser) {
            nameTextView.text = priest.name
            languagesTextView.text = priest.languages.joinToString(", ")

            // TODO: Load image using Glide/Picasso if photoUrl is available
            // For now, you can set a placeholder or leave it as is if using tools:srcCompat
            // Example:
            // if (!priest.photoUrl.isNullOrEmpty()) {
            // Glide.with(imageView.context).load(priest.photoUrl).into(imageView)
            // } else {
            // imageView.setImageResource(R.drawable.default_avatar) // a default placeholder
            // }
            // Using placeholder from XML for now via tools:srcCompat
        }
    }

    private class PriestDiffCallback : DiffUtil.ItemCallback<PriestUser>() {
        override fun areItemsTheSame(oldItem: PriestUser, newItem: PriestUser): Boolean {
            return oldItem.uid == newItem.uid
        }

        override fun areContentsTheSame(oldItem: PriestUser, newItem: PriestUser): Boolean {
            return oldItem == newItem
        }
    }
}
