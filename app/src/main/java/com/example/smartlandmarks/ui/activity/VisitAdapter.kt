package com.example.smartlandmarks.ui.activity

import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.core.content.ContextCompat
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import com.example.smartlandmarks.R
import com.example.smartlandmarks.databinding.ItemVisitBinding
import com.example.smartlandmarks.domain.model.Visit
import com.example.smartlandmarks.domain.model.VisitStatus
import com.example.smartlandmarks.utils.Formatters
import com.example.smartlandmarks.utils.visibleIf

/**
 * Renders the full visit lifecycle, not just completed ones.
 *
 * Showing QUEUED and PENDING states is the point: an offline visit stays visible and
 * explains itself instead of disappearing until it happens to sync.
 */
class VisitAdapter : ListAdapter<Visit, VisitAdapter.VisitViewHolder>(DIFF) {

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): VisitViewHolder {
        val binding = ItemVisitBinding.inflate(
            LayoutInflater.from(parent.context), parent, false
        )
        return VisitViewHolder(binding)
    }

    override fun onBindViewHolder(holder: VisitViewHolder, position: Int) {
        holder.bind(getItem(position))
    }

    class VisitViewHolder(
        private val binding: ItemVisitBinding
    ) : RecyclerView.ViewHolder(binding.root) {

        fun bind(visit: Visit) = with(binding) {
            val context = root.context

            landmarkTitleText.text = visit.landmarkTitle
            visitTimeText.text = Formatters.timestamp(visit.createdAt)

            val (statusLabel, statusColorRes) = when (visit.status) {
                VisitStatus.QUEUED -> context.getString(R.string.status_queued) to R.color.status_queued
                VisitStatus.PENDING -> context.getString(R.string.status_processing) to R.color.status_pending
                VisitStatus.DONE -> context.getString(R.string.status_done) to R.color.status_done
                VisitStatus.FAILED -> context.getString(R.string.status_failed) to R.color.status_failed
            }

            statusChip.text = statusLabel
            statusChip.setChipBackgroundColorResource(statusColorRes)

            distanceText.text = when (visit.status) {
                VisitStatus.DONE -> context.getString(
                    R.string.format_distance_away, Formatters.distance(visit.distanceMetres)
                )
                VisitStatus.QUEUED -> context.getString(R.string.distance_waiting_network)
                VisitStatus.PENDING -> context.getString(R.string.distance_calculating)
                VisitStatus.FAILED -> visit.errorMessage
                    ?: context.getString(R.string.distance_unavailable)
            }

            jobIdText.visibleIf(visit.jobId != null)
            jobIdText.text = visit.jobId?.let {
                context.getString(R.string.format_job_id, it)
            }.orEmpty()

            distanceText.setTextColor(
                ContextCompat.getColor(
                    context,
                    if (visit.status == VisitStatus.FAILED) R.color.status_failed
                    else R.color.text_secondary
                )
            )
        }
    }

    private companion object {
        val DIFF = object : DiffUtil.ItemCallback<Visit>() {
            override fun areItemsTheSame(oldItem: Visit, newItem: Visit): Boolean =
                oldItem.localId == newItem.localId

            override fun areContentsTheSame(oldItem: Visit, newItem: Visit): Boolean =
                oldItem == newItem
        }
    }
}
