package com.example.confessionapp.ui.adapters

import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import com.example.confessionapp.databinding.ItemAvailablePriestBinding // This layout needs to be created
import com.example.confessionapp.ui.viewmodels.PriestData

class PriestListAdapter(private val onItemClicked: (PriestData) -> Unit) :
    ListAdapter<PriestData, PriestListAdapter.PriestViewHolder>(PriestDiffCallback()) {

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): PriestViewHolder {
        val binding = ItemAvailablePriestBinding.inflate(LayoutInflater.from(parent.context), parent, false)
        return PriestViewHolder(binding)
    }

    override fun onBindViewHolder(holder: PriestViewHolder, position: Int) {
        val priest = getItem(position)
        holder.bind(priest)
        holder.itemView.setOnClickListener {
            onItemClicked(priest)
        }
    }

    class PriestViewHolder(private val binding: ItemAvailablePriestBinding) : RecyclerView.ViewHolder(binding.root) {
        fun bind(priest: PriestData) {
            // The priest.displayName now includes languages from the ViewModel modification
            binding.tvPriestNameAndLanguages.text = priest.displayName
            // Example: "Priest ABCD (Languages: English, Spanish)"
            // binding.tvPriestLanguages.text = priest.languages.joinToString(", ") // If languages were separate
        }
    }

    class PriestDiffCallback : DiffUtil.ItemCallback<PriestData>() {
        override fun areItemsTheSame(oldItem: PriestData, newItem: PriestData): Boolean {
            return oldItem.uid == newItem.uid
        }

        override fun areContentsTheSame(oldItem: PriestData, newItem: PriestData): Boolean {
            return oldItem == newItem // Relies on PriestData being a data class
        }
    }
}
