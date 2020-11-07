package de.deftk.lonet.mobile.fragments

import android.content.Intent
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ListView
import android.widget.ProgressBar
import android.widget.Toast
import androidx.core.view.isVisible
import androidx.swiperefreshlayout.widget.SwipeRefreshLayout
import de.deftk.lonet.api.model.feature.Task
import de.deftk.lonet.mobile.AuthStore
import de.deftk.lonet.mobile.R
import de.deftk.lonet.mobile.abstract.FeatureFragment
import de.deftk.lonet.mobile.activities.feature.TaskActivity
import de.deftk.lonet.mobile.adapter.TaskAdapter
import de.deftk.lonet.mobile.feature.AppFeature
import kotlinx.android.synthetic.main.fragment_tasks.*
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class TasksFragment : FeatureFragment(AppFeature.FEATURE_TASKS) {

    //TODO filters

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, bundle: Bundle?): View {
        CoroutineScope(Dispatchers.IO).launch {
            refreshTasks()
        }
        val view = inflater.inflate(R.layout.fragment_tasks, container, false)
        val swipeRefresh = view.findViewById<SwipeRefreshLayout>(R.id.tasks_swipe_refresh)
        val list = view.findViewById<ListView>(R.id.tasks_list)
        swipeRefresh.setOnRefreshListener {
            list.adapter = null
            CoroutineScope(Dispatchers.IO).launch {
                refreshTasks()
            }
        }
        list.setOnItemClickListener { _, _, position, _ ->
            val intent = Intent(context, TaskActivity::class.java)
            intent.putExtra(TaskActivity.EXTRA_TASK, list.getItemAtPosition(position) as Task)
            startActivity(intent)
        }
        return view
    }

    private suspend fun refreshTasks() {
        try {
            val tasks = AuthStore.appUser.getAllTasks().sortedByDescending { it.creationDate.time }
            withContext(Dispatchers.Main) {
                tasks_list?.adapter = TaskAdapter(requireContext(), tasks)
                tasks_empty?.isVisible = tasks.isEmpty()
                progress_tasks?.visibility = ProgressBar.INVISIBLE
                tasks_swipe_refresh?.isRefreshing = false
            }
        } catch (e: Exception) {
            withContext(Dispatchers.Main) {
                progress_tasks?.visibility = ProgressBar.INVISIBLE
                tasks_swipe_refresh?.isRefreshing = false
                Toast.makeText(context, getString(R.string.request_failed_other).format("No details"), Toast.LENGTH_LONG).show()
            }
        }
    }

}