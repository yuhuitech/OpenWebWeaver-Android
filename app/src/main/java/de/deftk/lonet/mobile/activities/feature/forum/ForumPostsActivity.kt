package de.deftk.lonet.mobile.activities.feature.forum

import android.content.Intent
import android.os.Bundle
import android.widget.ProgressBar
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.isVisible
import de.deftk.lonet.api.model.Group
import de.deftk.lonet.mobile.R
import de.deftk.lonet.mobile.adapter.ForumPostAdapter
import kotlinx.android.synthetic.main.activity_forum_posts.*
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.Serializable

/**
 * Activity holding a list of all forum posts for a specific group
 */
class ForumPostsActivity : AppCompatActivity() {

    //TODO icons for pinned & locked

    companion object {
        const val EXTRA_GROUP = "de.deftk.lonet.mobile.forum.group_extra"
    }

    private lateinit var group: Group

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_forum_posts)

        val extraGroup = intent.getSerializableExtra(EXTRA_GROUP) as? Group?
        if (extraGroup != null) {
            group = extraGroup
        } else {
            finish()
            return
        }

        setSupportActionBar(findViewById(R.id.toolbar))
        supportActionBar?.setDisplayHomeAsUpEnabled(true)
        supportActionBar?.setDisplayShowHomeEnabled(true)
        supportActionBar?.title = extraGroup.fullName ?: extraGroup.getName()

        forum_swipe_refresh.setOnRefreshListener {
            reloadForumPosts()
        }
        forum_list.setOnItemClickListener { _, _, position, _ ->
            val intent = Intent(this, ForumPostActivity::class.java)
            intent.putExtra(ForumPostActivity.EXTRA_FORUM_POST, forum_list.getItemAtPosition(position) as Serializable)
            startActivity(intent)
        }

        reloadForumPosts()
    }

    private fun reloadForumPosts() {
        forum_list.adapter = null
        forum_empty.visibility = TextView.GONE
        CoroutineScope(Dispatchers.IO).launch {
            loadForumPosts()
        }
    }

    private suspend fun loadForumPosts() {
        try {
            val posts = group.getForumPosts()
            withContext(Dispatchers.Main) {
                forum_list.adapter = ForumPostAdapter(this@ForumPostsActivity, posts)
                forum_empty.isVisible = posts.isEmpty()
                progress_forum.visibility = ProgressBar.GONE
                forum_swipe_refresh.isRefreshing = false
            }
        } catch (e: Exception) {
            withContext(Dispatchers.Main) {
                forum_empty.visibility = TextView.GONE
                progress_forum.visibility = ProgressBar.GONE
                forum_swipe_refresh.isRefreshing = false
                Toast.makeText(this@ForumPostsActivity, getString(R.string.request_failed_other).format("No details"), Toast.LENGTH_LONG).show()
            }
        }
    }

    // back button in toolbar functionality
    override fun onSupportNavigateUp(): Boolean {
        onBackPressed()
        return true
    }

}