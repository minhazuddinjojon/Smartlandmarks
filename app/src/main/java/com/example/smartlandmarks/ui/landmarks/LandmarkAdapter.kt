package com.example.smartlandmarks.ui.landmarks

import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import coil.load
import com.example.smartlandmarks.R
import com.example.smartlandmarks.databinding.ItemLandmarkBinding
import com.example.smartlandmarks.domain.model.Landmark
import com.example.smartlandmarks.utils.Formatters
import com.example.smartlandmarks.utils.ScoreColor

/**
 * ListAdapter + DiffUtil rather than notifyDataSetChanged, so the list animates changes
 * and does not rebind every row when a single score updates after a sync.
 */
class LandmarkAdapter(
    private val onClick: (Landmark) -> Unit
) : ListAdapter<Landmark, LandmarkAdapter.LandmarkViewHolder>(DIFF) {

    /** Refreshed whenever the data set changes, so score colours stay proportional. */
    private var minScore: Double = 0.0
    private var maxScore: Double = 0.0

    fun submitWithScale(items: List<Landmark>) {
        minScore = items.minOfOrNull { it.score } ?: 0.0
        maxScore = items.maxOfOrNull { it.score } ?: 0.0
        submitList(items)
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): LandmarkViewHolder {
        val binding = ItemLandmarkBinding.inflate(
            LayoutInflater.from(parent.context), parent, false
        )
        return LandmarkViewHolder(binding)
    }

    override fun onBindViewHolder(holder: LandmarkViewHolder, position: Int) {
        holder.bind(getItem(position), minScore, maxScore, onClick)
    }

    class LandmarkViewHolder(
        private val binding: ItemLandmarkBinding
    ) : RecyclerView.ViewHolder(binding.root) {

        fun bind(
            landmark: Landmark,
            minScore: Double,
            maxScore: Double,
            onClick: (Landmark) -> Unit
        ) = with(binding) {
            titleText.text = landmark.title
            scoreText.text = Formatters.score(landmark.score)
            visitCountText.text = root.context.getString(
                R.string.format_visit_count, landmark.visitCount
            )
            averageDistanceText.text = root.context.getString(
                R.string.format_average_distance,
                Formatters.distance(landmark.averageDistance)
            )

            scoreBadge.setCardBackgroundColor(
                ScoreColor.forScore(landmark.score, minScore, maxScore)
            )

            landmarkImage.load(landmark.imageUrl) {
                crossfade(true)
                placeholder(R.drawable.ic_image_placeholder)
                error(R.drawable.ic_image_placeholder)
            }

            root.setOnClickListener { onClick(landmark) }
        }
    }

    private companion object {
        val DIFF = object : DiffUtil.ItemCallback<Landmark>() {
            override fun areItemsTheSame(oldItem: Landmark, newItem: Landmark): Boolean =
                oldItem.id == newItem.id

            override fun areContentsTheSame(oldItem: Landmark, newItem: Landmark): Boolean =
                oldItem == newItem
        }
    }
}
