package com.example.myloggerv03

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.appcompat.widget.AppCompatImageButton
import androidx.recyclerview.widget.RecyclerView

class TripAdapter(
    private val trips: MutableList<Trip>,
    private val onItemClicked: (Trip) -> Unit,
    private val onDeleteClicked: (Trip) -> Unit
) : RecyclerView.Adapter<TripAdapter.TripViewHolder>() {

    class TripViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
        val tripName: TextView = itemView.findViewById(R.id.trip_name)
        val tripDate: TextView = itemView.findViewById(R.id.trip_date)
        val deleteButton: AppCompatImageButton = itemView.findViewById(R.id.delete_trip_button)
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): TripViewHolder {
        val view = LayoutInflater.from(parent.context)
            .inflate(R.layout.item_saved_trip, parent, false)
        return TripViewHolder(view)
    }

    override fun onBindViewHolder(holder: TripViewHolder, position: Int) {
        val trip = trips[position]
        holder.tripName.text = trip.name
        holder.tripDate.text = trip.date

        holder.itemView.setOnClickListener {
            onItemClicked(trip)
        }

        holder.deleteButton.setOnClickListener {
            onDeleteClicked(trip)
            trips.removeAt(position)
            notifyItemRemoved(position)
            notifyItemRangeChanged(position, itemCount)
        }
    }

    override fun getItemCount(): Int = trips.size
}