package com.example.confessionapp.ui.adapters

import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import com.example.confessionapp.data.ChatMessage
import com.example.confessionapp.databinding.ItemChatMessageBinding
import java.text.SimpleDateFormat
import java.util.*

class ChatAdapter(private val currentUserId: String) : ListAdapter<ChatMessage, ChatAdapter.ChatMessageViewHolder>(ChatMessageDiffCallback()) {

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ChatMessageViewHolder {
        val binding = ItemChatMessageBinding.inflate(LayoutInflater.from(parent.context), parent, false)
        return ChatMessageViewHolder(binding)
    }

    override fun onBindViewHolder(holder: ChatMessageViewHolder, position: Int) {
        holder.bind(getItem(position), currentUserId)
    }

    class ChatMessageViewHolder(private val binding: ItemChatMessageBinding) : RecyclerView.ViewHolder(binding.root) {
        private val dateFormat = SimpleDateFormat("hh:mm a", Locale.getDefault())

        fun bind(chatMessage: ChatMessage, currentUserId: String) {
            binding.tvChatText.text = chatMessage.text
            binding.tvChatTimestamp.text = dateFormat.format(Date(chatMessage.timestamp))

            // Adjust alignment or style based on sender
            if (chatMessage.senderId == currentUserId) {
                binding.tvChatSender.text = "You:" // Or hide sender for current user's messages
                binding.chatMessageRoot.gravity = android.view.Gravity.END
                binding.chatMessageContainer.setBackgroundResource(com.example.confessionapp.R.drawable.bg_chat_message_sent)
                // Optionally change text colors for sent messages if background is dark
                // binding.tvChatSender.setTextColor(ContextCompat.getColor(itemView.context, R.color.white))
                // binding.tvChatText.setTextColor(ContextCompat.getColor(itemView.context, R.color.white))
                // binding.tvChatTimestamp.setTextColor(ContextCompat.getColor(itemView.context, R.color.white_ish_for_timestamp))

            } else {
                binding.tvChatSender.text = "${chatMessage.senderDisplayName}:"
                binding.chatMessageRoot.gravity = android.view.Gravity.START
                binding.chatMessageContainer.setBackgroundResource(com.example.confessionapp.R.drawable.bg_chat_message_received)
                // Reset text colors if changed for sent messages
                // binding.tvChatSender.setTextColor(ContextCompat.getColor(itemView.context, R.color.default_text_color))
                // binding.tvChatText.setTextColor(ContextCompat.getColor(itemView.context, R.color.default_text_color))
                // binding.tvChatTimestamp.setTextColor(ContextCompat.getColor(itemView.context, R.color.default_timestamp_color))
            }
        }
    }

    class ChatMessageDiffCallback : DiffUtil.ItemCallback<ChatMessage>() {
        override fun areItemsTheSame(oldItem: ChatMessage, newItem: ChatMessage): Boolean {
            return oldItem.messageId == newItem.messageId
        }

        override fun areContentsTheSame(oldItem: ChatMessage, newItem: ChatMessage): Boolean {
            return oldItem == newItem
        }
    }
}
