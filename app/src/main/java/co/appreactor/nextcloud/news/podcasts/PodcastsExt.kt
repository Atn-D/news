package co.appreactor.nextcloud.news.podcasts

import android.content.Context
import android.content.Intent
import androidx.core.content.FileProvider
import androidx.fragment.app.Fragment
import co.appreactor.nextcloud.news.db.Entry
import co.appreactor.nextcloud.news.db.EntryWithoutSummary
import java.io.File

fun EntryWithoutSummary.isPodcast(): Boolean {
    return enclosureLinkType.startsWith("audio")
}

fun Entry.getPodcastFile(context: Context): File {
    val podcasts = File(context.externalCacheDir, "podcasts")
    podcasts.mkdir()

    val fileName = "$id-${enclosureLink.split("/").last()}"
    return File(podcasts, fileName)
}

fun Fragment.playPodcast(podcast: Entry) {
    val fileUri = FileProvider.getUriForFile(
        requireContext(),
        "${requireContext().packageName}.fileprovider",
        podcast.getPodcastFile(requireContext())
    )

    val intent = Intent().apply {
        action = Intent.ACTION_VIEW
        data = fileUri
        addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
    }

    startActivity(intent)
}