package co.appreactor.nextcloud.news.feeditems

import android.os.Bundle
import android.util.DisplayMetrics
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.core.view.isVisible
import androidx.fragment.app.Fragment
import androidx.lifecycle.lifecycleScope
import androidx.navigation.fragment.findNavController
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import co.appreactor.nextcloud.news.R
import co.appreactor.nextcloud.news.common.showDialog
import co.appreactor.nextcloud.news.podcasts.playPodcast
import kotlinx.android.synthetic.main.fragment_feed_items.*
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.flow.conflate
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import org.koin.android.viewmodel.ext.android.viewModel
import timber.log.Timber

class FeedItemsFragment : Fragment() {

    private val model: FeedItemsFragmentModel by viewModel()

    private val adapter = FeedItemsAdapter(
        scope = lifecycleScope,
        callback = object : FeedItemsAdapterCallback {
            override fun onItemClick(item: FeedItemsAdapterItem) {
                val action = FeedItemsFragmentDirections.actionNewsFragmentToNewsItemFragment(item.id)
                findNavController().navigate(action)
            }

            override fun onDownloadPodcastClick(item: FeedItemsAdapterItem) {
                lifecycleScope.launchWhenResumed {
                    runCatching {
                        model.downloadPodcast(item.id)
                    }.onFailure {
                        Timber.e(it)
                        showDialog(R.string.error, it.message ?: "")
                    }
                }
            }

            override fun onPlayPodcastClick(item: FeedItemsAdapterItem) {
                lifecycleScope.launch {
                    runCatching {
                        playPodcast(model.getFeedItem(item.id)!!)
                    }.onFailure {
                        Timber.e(it)
                        showDialog(R.string.error, it.message ?: "")
                    }
                }
            }
        }
    ).apply {
        registerAdapterDataObserver(object : RecyclerView.AdapterDataObserver() {
            override fun onItemRangeInserted(positionStart: Int, itemCount: Int) {
                if (positionStart == 0) {
                    (listView.layoutManager as LinearLayoutManager).scrollToPositionWithOffset(0, 0)
                }
            }
        })
    }

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? {
        return inflater.inflate(
            R.layout.fragment_feed_items,
            container,
            false
        )
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        initToolbar()

        swipeRefresh.setOnRefreshListener {
            lifecycleScope.launch {
                runCatching {
                    model.performFullSync()
                }.onFailure {
                    Timber.e(it)
                    showDialog(R.string.error, it.message ?: "")
                }

                swipeRefresh.isRefreshing = false
            }
        }

        lifecycleScope.launchWhenResumed {
            showFeedItems()
        }
    }

    private fun initToolbar() {
        toolbar.setOnMenuItemClickListener {
            if (it.itemId == R.id.showReadNews) {
                lifecycleScope.launch {
                    model.setShowReadNews(!model.getShowReadNews().first())
                }

                return@setOnMenuItemClickListener true
            }

            false
        }

        lifecycleScope.launchWhenResumed {
            model.getShowReadNews().collect { show ->
                val item = toolbar.menu.findItem(R.id.showReadNews)

                if (show) {
                    item.setIcon(R.drawable.ic_baseline_visibility_24)
                    item.setTitle(R.string.hide_read_news)
                } else {
                    item.setIcon(R.drawable.ic_baseline_visibility_off_24)
                    item.setTitle(R.string.show_read_news)
                }
            }
        }
    }

    private suspend fun showFeedItems() {
        progress.isVisible = true

        runCatching {
            model.performInitialSyncIfNoData()
        }.onFailure {
            Timber.e(it)
            progress.isVisible = false
            showDialog(R.string.error, it.message ?: "")
        }

        listView.setHasFixedSize(true)
        listView.layoutManager = LinearLayoutManager(context)
        listView.adapter = adapter
        listView.addItemDecoration(FeedItemsAdapterDecoration(resources.getDimensionPixelSize(R.dimen.feed_items_cards_gap)))

        val displayMetrics = DisplayMetrics()
        requireActivity().windowManager.defaultDisplay.getMetrics(displayMetrics)
        adapter.screenWidth = displayMetrics.widthPixels

        model
            .getFeedItems()
            .catch {
                Timber.e(it)
                progress.isVisible = false
                showDialog(R.string.error, it.message ?: "")
            }
            .conflate()
            .collect { feedItems ->
                if (model.isInitialSyncCompleted()) {
                    progress.isVisible = false
                }

                empty.isVisible = feedItems.isEmpty() && model.isInitialSyncCompleted()

                adapter.submitList(feedItems)
            }
    }
}