package com.example.confessionapp.ui

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView
import com.example.confessionapp.R
import com.example.confessionapp.data.models.PriestUser

class PriestAdapter(private var priests: List<PriestUser>) :
    RecyclerView.Adapter<PriestAdapter.PriestViewHolder>() {

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): PriestViewHolder {
        val view = LayoutInflater.from(parent.context)
            .inflate(R.layout.item_priest_card, parent, false)
        return PriestViewHolder(view)
    }

    override fun onBindViewHolder(holder: PriestViewHolder, position: Int) {
        val priest = priests[position]
        holder.bind(priest)
    }

    override fun getItemCount(): Int = priests.size

    fun updateData(newPriests: List<PriestUser>) {
        priests = newPriests
        notifyDataSetChanged() // Consider using DiffUtil for better performance
    }

    class PriestViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
        private val nameTextView: TextView = itemView.findViewById(R.id.priest_name_text_view)
        private val languagesTextView: TextView = itemView.findViewById(R.id.priest_languages_text_view)

        fun bind(priest: PriestUser) {
            nameTextView.text = priest.name.ifEmpty { "N/A" }
            if (priest.languages.isNotEmpty()) {
                languagesTextView.text = "Languages: ${priest.languages.joinToString(", ")}"
                languagesTextView.visibility = View.VISIBLE
            } else {
                languagesTextView.text = "Languages: Not specified"
                // Or hide it: languagesTextView.visibility = View.GONE
            }
        }
    }
}
